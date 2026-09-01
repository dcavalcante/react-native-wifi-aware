package com.wifiaware

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkInfo
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.WritableMap
import java.util.IdentityHashMap
import java.util.UUID
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class WifiAwareModule(reactContext: ReactApplicationContext) : NativeWifiAwareSpec(reactContext) {
  // All mutable Aware state is serialized on this handler.
  private val handler = Handler(Looper.getMainLooper())
  @RequiresApi(26) private val handles = AwareHandleRegistry<WifiAwareSession, DiscoverySession>()
  @RequiresApi(26) private val peers = mutableMapOf<String, IdentityHashMap<PeerHandle, String>>()
  @RequiresApi(26) private val pendingMessages = PendingMessageRegistry<Promise>()
  private val socketExecutor = ThreadPoolExecutor(
    1,
    1,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(MAX_QUEUED_SOCKET_TASKS),
    ThreadPoolExecutor.AbortPolicy(),
  )
  private val dataPaths = DataPathRegistry<DataPathResources>()
  private val pendingAttaches = mutableMapOf<String, Promise>()
  private val pendingDiscoveries = mutableMapOf<String, Pair<String, Promise>>()
  @RequiresApi(26) private val pendingDiscoverySecurityModes = mutableMapOf<String, String>()
  @RequiresApi(26) private val discoverySecurityModes = mutableMapOf<String, String>()
  private var receiverRegistered = false

  private data class DataPathResources(
    val discoveryHandle: String,
    val manager: ConnectivityManager,
    val role: String,
    var callback: ConnectivityManager.NetworkCallback? = null,
    var callbackRegistered: Boolean = false,
    var network: Network? = null,
    var serverSocket: ServerSocket? = null,
    var socket: Socket? = null,
    var socketTask: Future<*>? = null,
  )

  private val availabilityReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action == WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED) {
        handler.post { invalidateUnavailableState() }
      }
    }
  }

  init { registerAvailabilityReceiver() }

  override fun getCapabilities(): WritableMap {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return capabilityMap(false, false, false)
    return getCapabilitiesApi26()
  }

  override fun attach(promise: Promise) {
    handler.post {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) promise.reject("UNSUPPORTED", "Wi-Fi Aware requires API 26")
      else attachApi26(promise)
    }
  }

  @RequiresApi(26)
  private fun attachApi26(promise: Promise) {
    val manager = manager(promise) ?: return
    val handle = newHandle("aware")
    handles.beginSession(handle)
    pendingAttaches[handle] = promise
    try {
      manager.attach(object : AttachCallback() {
        override fun onAttached(session: WifiAwareSession) {
          handler.post {
            if (!handles.completeSession(handle, session)) {
              session.close() // Late callback after close or invalidation.
              return@post
            }
            pendingAttaches.remove(handle)?.resolve(handle)
          }
        }
        override fun onAttachFailed() {
          handler.post {
            handles.closeSession(handle)
            pendingAttaches.remove(handle)?.reject("ATTACH_FAILED", "Wi-Fi Aware attach failed")
          }
        }
      }, handler)
    } catch (error: SecurityException) {
      handles.closeSession(handle)
      pendingAttaches.remove(handle)?.reject("PERMISSION_DENIED", "Missing Wi-Fi Aware permission", error)
    } catch (error: RuntimeException) {
      handles.closeSession(handle)
      pendingAttaches.remove(handle)?.reject("INTERNAL_ERROR", "Wi-Fi Aware attach failed", error)
    }
  }

  override fun closeSession(handle: String, promise: Promise) {
    handler.post {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) promise.reject("UNSUPPORTED", "Wi-Fi Aware requires API 26")
      else closeSessionApi26(handle, promise)
    }
  }

  @RequiresApi(26)
  private fun closeSessionApi26(handle: String, promise: Promise) {
    when (handles.sessionState(handle)) {
      AwareHandleRegistry.HandleState.UNKNOWN -> promise.reject("INVALID_HANDLE", "Unknown session handle")
      AwareHandleRegistry.HandleState.CLOSED -> promise.resolve(null)
      AwareHandleRegistry.HandleState.LIVE -> {
        val childHandles = handles.discoveryHandlesForSession(handle)
        pendingDiscoveries.filterValues { it.first == handle }.keys.toList().forEach {
          pendingDiscoveries.remove(it)?.second?.reject("SESSION_CLOSED", "Session closed before discovery started")
        }
        val (session, discoveries) = handles.closeSession(handle)
        childHandles.forEach {
          closeDataPathsForDiscovery(it, "closed", "Parent session closed")
          rejectPendingMessagesForDiscovery(it, "SESSION_CLOSED", "Session closed before message completed")
          peers.remove(it)
          pendingDiscoverySecurityModes.remove(it)
          discoverySecurityModes.remove(it)
        }
        discoveries.forEach { it.value.close() }
        session?.close()
        promise.resolve(null)
      }
    }
  }

  override fun publish(handle: String, options: ReadableMap, promise: Promise) =
    startDiscovery(handle, options, promise, subscriber = false)
  override fun subscribe(handle: String, options: ReadableMap, promise: Promise) =
    startDiscovery(handle, options, promise, subscriber = true)

  override fun presentPairing(handle: String, promise: Promise) {
    // Android's completed stages have no equivalent system pairing surface.
    // Keep the shared Codegen contract explicit rather than pretending that a
    // discovery peer is a paired Apple peer.
    promise.reject("UNSUPPORTED", "System pairing UI is not implemented on Android")
  }

  private fun startDiscovery(parent: String, options: ReadableMap, promise: Promise, subscriber: Boolean) {
    handler.post {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) promise.reject("UNSUPPORTED", "Wi-Fi Aware requires API 26")
      else startDiscoveryApi26(parent, options, promise, subscriber)
    }
  }

  @RequiresApi(26)
  private fun startDiscoveryApi26(parent: String, options: ReadableMap, promise: Promise, subscriber: Boolean) {
    if (!isAwareAvailable()) {
      disposeNativeState("UNAVAILABLE", "Wi-Fi Aware became unavailable")
      promise.reject("UNAVAILABLE", "Wi-Fi Aware is unavailable")
      return
    }
    if (!hasDiscoveryPermission()) {
      promise.reject("PERMISSION_DENIED", "Discovery permission is not granted")
      return
    }
    val session = handles.session(parent)
    if (session == null) {
      val code = if (handles.sessionState(parent) == AwareHandleRegistry.HandleState.CLOSED) "SESSION_CLOSED" else "INVALID_HANDLE"
      promise.reject(code, "Session is not live")
      return
    }
    val serviceName = try { options.getString("serviceName") } catch (_: Exception) { null }
    if (serviceName.isNullOrBlank()) {
      promise.reject("INVALID_ARGUMENT", "A non-empty serviceName is required")
      return
    }
    val securityMode = try { options.getString("securityMode") } catch (_: Exception) { null }
    if (securityMode != "psk" && securityMode != "paired") {
      promise.reject("INVALID_ARGUMENT", "Discovery securityMode must be psk or paired")
      return
    }
    if (securityMode == "paired" && !isFrameworkOffloadedPairingSupported()) {
      promise.reject("UNSUPPORTED", "Paired Wi-Fi Aware discovery requires Android 17.2 and supported Wi-Fi Aware pairing hardware")
      return
    }
    val discoveryHandle = newHandle("discovery")
    handles.beginDiscovery(discoveryHandle, parent)
    pendingDiscoveries[discoveryHandle] = parent to promise
    pendingDiscoverySecurityModes[discoveryHandle] = securityMode
    val callback = object : DiscoverySessionCallback() {
      override fun onPublishStarted(discovery: PublishDiscoverySession) {
        handler.post { registerDiscovery(discoveryHandle, discovery) }
      }
      override fun onSubscribeStarted(discovery: SubscribeDiscoverySession) {
        handler.post { registerDiscovery(discoveryHandle, discovery) }
      }
      override fun onSessionConfigFailed() {
        handler.post { failDiscovery(discoveryHandle, "DISCOVERY_FAILED", "Discovery configuration failed") }
      }
      override fun onSessionTerminated() {
        handler.post { terminateDiscovery(discoveryHandle) }
      }
      override fun onServiceDiscovered(peer: PeerHandle, info: ByteArray?, filter: List<ByteArray>?) {
        if (!subscriber) return
        handler.post {
          val peerHandle = registerPeer(discoveryHandle, peer) ?: return@post
          emitOnPeerFound(Arguments.createMap().apply {
            putString("eventType", "peerFound")
            putString("discoverySessionHandle", discoveryHandle)
            putString("peerHandle", peerHandle)
            putArray("payload", Arguments.createArray())
          })
        }
      }
      override fun onMessageSendSucceeded(messageId: Int) {
        handler.post {
          pendingMessages.complete(discoveryHandle, messageId)?.value?.resolve(null)
        }
      }
      override fun onMessageSendFailed(messageId: Int) {
        handler.post {
          pendingMessages.complete(discoveryHandle, messageId)?.value?.reject(
            "MESSAGE_SEND_FAILED",
            "Wi-Fi Aware message send failed"
          )
        }
      }
      override fun onMessageReceived(peer: PeerHandle, message: ByteArray) {
        handler.post {
          val peerHandle = registerPeer(discoveryHandle, peer) ?: return@post
          emitOnPeerFound(Arguments.createMap().apply {
            putString("eventType", "messageReceived")
            putString("discoverySessionHandle", discoveryHandle)
            putString("peerHandle", peerHandle)
            putArray("payload", Arguments.createArray().apply {
              message.forEach { pushInt(it.toInt() and 0xff) }
            })
          })
        }
      }
    }
    try {
      if (subscriber) {
        val config = SubscribeConfig.Builder().setServiceName(serviceName)
        if (securityMode == "paired") enableFrameworkOffloadedPairing(config)
        session.subscribe(config.build(), callback, handler)
      } else {
        val config = PublishConfig.Builder().setServiceName(serviceName)
        if (securityMode == "paired") enableFrameworkOffloadedPairing(config)
        session.publish(config.build(), callback, handler)
      }
    } catch (error: IllegalArgumentException) {
      failDiscovery(discoveryHandle, "INVALID_ARGUMENT", "Invalid discovery configuration", error)
    } catch (error: SecurityException) {
      failDiscovery(discoveryHandle, "PERMISSION_DENIED", "Discovery permission is not granted", error)
    } catch (error: RuntimeException) {
      failDiscovery(discoveryHandle, "INTERNAL_ERROR", "Unable to start discovery", error)
    }
  }

  @RequiresApi(26)
  private fun registerDiscovery(handle: String, discovery: DiscoverySession) {
    if (!handles.completeDiscovery(handle, discovery)) {
      discovery.close()
      return
    }
    peers[handle] = IdentityHashMap()
    discoverySecurityModes[handle] = pendingDiscoverySecurityModes.remove(handle) ?: run {
      closeDiscovery(handle)
      return
    }
    pendingDiscoveries.remove(handle)?.second?.resolve(handle)
  }

  @RequiresApi(26)
  private fun failDiscovery(handle: String, code: String, message: String, error: Throwable? = null) {
    handles.closeDiscovery(handle)
    peers.remove(handle)
    pendingDiscoverySecurityModes.remove(handle)
    discoverySecurityModes.remove(handle)
    val promise = pendingDiscoveries.remove(handle)?.second ?: return
    if (error == null) promise.reject(code, message) else promise.reject(code, message, error)
  }

  override fun closeDiscoverySession(handle: String, promise: Promise) {
    handler.post {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) promise.reject("UNSUPPORTED", "Wi-Fi Aware requires API 26")
      else closeDiscoverySessionApi26(handle, promise)
    }
  }

  @RequiresApi(26)
  private fun closeDiscoverySessionApi26(handle: String, promise: Promise) {
    when (handles.discoveryState(handle)) {
      AwareHandleRegistry.HandleState.UNKNOWN -> promise.reject("INVALID_HANDLE", "Unknown discovery handle")
      AwareHandleRegistry.HandleState.CLOSED -> promise.resolve(null)
      AwareHandleRegistry.HandleState.LIVE -> { closeDiscovery(handle); promise.resolve(null) }
    }
  }

  @RequiresApi(26)
  private fun closeDiscovery(handle: String, closeNative: Boolean = true) {
    val record = handles.closeDiscovery(handle) ?: return
    closeDataPathsForDiscovery(handle, "closed", "Discovery session closed")
    rejectPendingMessagesForDiscovery(handle, "DISCOVERY_CLOSED", "Discovery closed before message completed")
    peers.remove(handle)
    discoverySecurityModes.remove(handle)
    pendingDiscoverySecurityModes.remove(handle)
    if (closeNative) record.value.close()
  }

  @RequiresApi(26)
  private fun terminateDiscovery(handle: String) {
    val record = handles.closeDiscovery(handle)
    closeDataPathsForDiscovery(handle, "closed", "Discovery session terminated")
    rejectPendingMessagesForDiscovery(handle, "DISCOVERY_CLOSED", "Discovery terminated before message completed")
    peers.remove(handle)
    discoverySecurityModes.remove(handle)
    pendingDiscoverySecurityModes.remove(handle)
    if (record == null) {
      pendingDiscoveries.remove(handle)?.second?.reject(
        "DISCOVERY_FAILED",
        "Discovery terminated before it started"
      )
    }
  }

  override fun sendMessage(discoverySessionHandle: String, peerHandle: String, payload: ReadableArray, promise: Promise) {
    handler.post {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        promise.reject("UNSUPPORTED", "Wi-Fi Aware requires API 26")
      } else {
        sendMessageApi26(discoverySessionHandle, peerHandle, payload, promise)
      }
    }
  }

  @RequiresApi(26)
  private fun sendMessageApi26(discoveryHandle: String, peerHandle: String, payload: ReadableArray, promise: Promise) {
    if (!isAwareAvailable()) {
      disposeNativeState("UNAVAILABLE", "Wi-Fi Aware became unavailable")
      promise.reject("UNAVAILABLE", "Wi-Fi Aware is unavailable")
      return
    }
    if (!hasDiscoveryPermission()) {
      promise.reject("PERMISSION_DENIED", "Discovery permission is not granted")
      return
    }
    val discovery = handles.discovery(discoveryHandle)?.value
    if (discovery == null) {
      val code = if (handles.discoveryState(discoveryHandle) == AwareHandleRegistry.HandleState.CLOSED) "DISCOVERY_CLOSED" else "INVALID_HANDLE"
      promise.reject(code, "Discovery session is not live")
      return
    }
    val peer = peers[discoveryHandle]?.entries?.firstOrNull { it.value == peerHandle }?.key
    if (peer == null) {
      promise.reject("INVALID_HANDLE", "Peer does not belong to this discovery session")
      return
    }
    val bytes = bytePayload(payload, promise) ?: return
    val maxLength = try {
      (reactApplicationContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager)
        ?.characteristics?.maxServiceSpecificInfoLength
    } catch (error: SecurityException) {
      promise.reject("PERMISSION_DENIED", "Missing Wi-Fi Aware permission", error)
      return
    } catch (error: RuntimeException) {
      promise.reject("INTERNAL_ERROR", "Unable to read Wi-Fi Aware characteristics", error)
      return
    }
    if (maxLength != null && bytes.size > maxLength) {
      promise.reject("INVALID_ARGUMENT", "Message exceeds the device Wi-Fi Aware payload limit")
      return
    }
    val messageId = pendingMessages.begin(discoveryHandle, promise)
    try {
      discovery.sendMessage(peer, messageId, bytes)
    } catch (error: IllegalArgumentException) {
      pendingMessages.complete(discoveryHandle, messageId)?.value?.reject("INVALID_ARGUMENT", "Invalid Wi-Fi Aware message", error)
    } catch (error: SecurityException) {
      pendingMessages.complete(discoveryHandle, messageId)?.value?.reject("PERMISSION_DENIED", "Discovery permission is not granted", error)
    } catch (error: RuntimeException) {
      pendingMessages.complete(discoveryHandle, messageId)?.value?.reject("INTERNAL_ERROR", "Unable to send Wi-Fi Aware message", error)
    }
  }

  private fun bytePayload(payload: ReadableArray, promise: Promise): ByteArray? {
    val bytes = ByteArray(payload.size())
    for (index in 0 until payload.size()) {
      if (payload.getType(index) != ReadableType.Number) {
        promise.reject("INVALID_ARGUMENT", "Message payload must contain byte values")
        return null
      }
      val value = payload.getDouble(index)
      if (!value.isFinite() || value % 1 != 0.0 || value < 0 || value > 255) {
        promise.reject("INVALID_ARGUMENT", "Message payload values must be integers from 0 to 255")
        return null
      }
      bytes[index] = value.toInt().toByte()
    }
    return bytes
  }

  override fun openDataPath(
    discoverySessionHandle: String,
    peerHandle: String,
    options: ReadableMap,
    promise: Promise,
  ) {
    handler.post {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        promise.reject("UNSUPPORTED", "Wi-Fi Aware data paths require API 29")
      } else {
        openDataPathApi29(discoverySessionHandle, peerHandle, options, promise)
      }
    }
  }

  @RequiresApi(29)
  private fun openDataPathApi29(
    discoveryHandle: String,
    peerHandle: String,
    options: ReadableMap,
    promise: Promise,
  ) {
    if (!isAwareAvailable()) {
      disposeNativeState("UNAVAILABLE", "Wi-Fi Aware became unavailable")
      promise.reject("UNAVAILABLE", "Wi-Fi Aware is unavailable")
      return
    }
    if (!hasDiscoveryPermission()) {
      promise.reject("PERMISSION_DENIED", "Discovery permission is not granted")
      return
    }
    val discovery = handles.discovery(discoveryHandle)?.value
    if (discovery == null) {
      val code = if (handles.discoveryState(discoveryHandle) == AwareHandleRegistry.HandleState.CLOSED) "DISCOVERY_CLOSED" else "INVALID_HANDLE"
      promise.reject(code, "Discovery session is not live")
      return
    }
    if (dataPaths.handlesForDiscovery(discoveryHandle).isNotEmpty()) {
      promise.reject("DATA_PATH_FAILED", "A data path is already active for this discovery session")
      return
    }
    val role = try { options.getString("role") } catch (_: Exception) { null }
    val securityMode = try { options.getString("securityMode") } catch (_: Exception) { null }
    val passphrase = try { options.getString("passphrase") } catch (_: Exception) { null }
    if (role != "server" && role != "client") {
      promise.reject("INVALID_ARGUMENT", "Data-path options require a server/client role")
      return
    }
    if (securityMode != "psk" && securityMode != "paired") {
      promise.reject("INVALID_ARGUMENT", "Data-path securityMode must be psk or paired")
      return
    }
    if (discoverySecurityModes[discoveryHandle] != securityMode) {
      promise.reject("INVALID_ARGUMENT", "Data-path security must match discovery security")
      return
    }
    if (securityMode == "psk" && passphrase.isNullOrBlank()) {
      promise.reject("INVALID_ARGUMENT", "PSK data paths require a non-empty passphrase")
      return
    }
    if (securityMode == "paired" && !isFrameworkOffloadedPairingSupported()) {
      promise.reject("UNSUPPORTED", "Paired Wi-Fi Aware data paths require Android 17.2 and supported Wi-Fi Aware pairing hardware")
      return
    }
    if (role == "server" && discovery !is PublishDiscoverySession) {
      promise.reject("INVALID_ARGUMENT", "The data-path server must use a publisher discovery session")
      return
    }
    if (role == "client" && discovery !is SubscribeDiscoverySession) {
      promise.reject("INVALID_ARGUMENT", "The data-path client must use a subscriber discovery session")
      return
    }
    val peer = peers[discoveryHandle]?.entries?.firstOrNull { it.value == peerHandle }?.key
    val pairedPublisherServer = role == "server" && securityMode == "paired"
    if (peer == null && !pairedPublisherServer) {
      promise.reject("INVALID_HANDLE", "Peer does not belong to this discovery session")
      return
    }
    val connectivity = reactApplicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (connectivity == null) {
      promise.reject("DATA_PATH_FAILED", "Connectivity Manager is unavailable")
      return
    }
    val serverSocket = if (role == "server") {
      try {
        ServerSocket(0)
      } catch (error: Exception) {
        promise.reject("DATA_PATH_FAILED", "Unable to create data-path server socket", error)
        return
      }
    } else {
      null
    }
    val handle = newHandle("data-path")
    val resources = DataPathResources(discoveryHandle, connectivity, role, serverSocket = serverSocket)
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        handler.post { handleDataPathAvailable(handle, network) }
      }

      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        handler.post { handleDataPathCapabilities(handle, network, capabilities) }
      }

      override fun onUnavailable() {
        handler.post { failDataPath(handle, "Network request was unavailable", callbackAlreadyReleased = true) }
      }

      override fun onLost(network: Network) {
        handler.post { retireDataPath(handle, "lost", "Wi-Fi Aware network was lost") }
      }
    }
    resources.callback = callback
    dataPaths.begin(handle, discoveryHandle)
    dataPaths.complete(handle, resources)
    val request = try {
      val specifierBuilder = (if (pairedPublisherServer) {
        WifiAwareNetworkSpecifier.Builder(discovery as PublishDiscoverySession)
      } else {
        WifiAwareNetworkSpecifier.Builder(discovery, peer!!)
      }).apply {
          if (securityMode == "psk") setPskPassphrase(passphrase!!)
          if (role == "server") {
            setPort(serverSocket!!.localPort)
            setTransportProtocol(TCP_PROTOCOL)
          }
        }
      val specifier = specifierBuilder
        .build()
      NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
        .setNetworkSpecifier(specifier)
        .build()
    } catch (error: IllegalArgumentException) {
      dataPaths.close(handle)
      closeDataPathResources(resources)
      promise.reject("INVALID_ARGUMENT", "Invalid Wi-Fi Aware data-path options", error)
      return
    } catch (error: RuntimeException) {
      dataPaths.close(handle)
      closeDataPathResources(resources)
      promise.reject("DATA_PATH_FAILED", "Unable to create Wi-Fi Aware network request", error)
      return
    }
    try {
      connectivity.requestNetwork(request, callback, handler, DATA_PATH_TIMEOUT_MS)
      resources.callbackRegistered = true
      promise.resolve(handle)
    } catch (error: SecurityException) {
      dataPaths.close(handle)
      closeDataPathResources(resources)
      promise.reject("PERMISSION_DENIED", "Missing Wi-Fi Aware network permission", error)
    } catch (error: RuntimeException) {
      dataPaths.close(handle)
      closeDataPathResources(resources)
      promise.reject("DATA_PATH_FAILED", "Unable to request Wi-Fi Aware network", error)
    }
  }

  override fun closeDataPath(handle: String, promise: Promise) {
    handler.post {
      when (dataPaths.state(handle)) {
        DataPathRegistry.HandleState.UNKNOWN -> promise.reject("INVALID_HANDLE", "Unknown data-path handle")
        DataPathRegistry.HandleState.CLOSED -> promise.resolve(null)
        DataPathRegistry.HandleState.LIVE -> {
          retireDataPath(handle, "closed", "Data path closed")
          promise.resolve(null)
        }
      }
    }
  }

  @RequiresApi(29)
  private fun handleDataPathAvailable(handle: String, network: Network) {
    val resources = dataPaths.path(handle)?.value ?: return
    resources.network = network
    if (resources.role == "server") startServerAccept(handle, resources)
  }

  @RequiresApi(29)
  private fun handleDataPathCapabilities(
    handle: String,
    network: Network,
    capabilities: NetworkCapabilities,
  ) {
    val resources = dataPaths.path(handle)?.value ?: return
    if (resources.role != "client" || resources.network != network || resources.socketTask != null) return
    val peerInfo = capabilities.transportInfo as? WifiAwareNetworkInfo
    if (peerInfo == null || peerInfo.peerIpv6Addr == null || peerInfo.port <= 0) {
      failDataPath(handle, "Wi-Fi Aware peer address or port was unavailable")
      return
    }
    try {
      resources.socketTask = socketExecutor.submit {
        try {
          val socket = network.socketFactory.createSocket(peerInfo.peerIpv6Addr, peerInfo.port)
          handler.post {
            val active = dataPaths.path(handle)?.value
            if (active !== resources) {
              socket.close()
              return@post
            }
            resources.socket = socket
            resources.socketTask = null
            emitDataPathState(handle, "connected", "Client socket connected")
          }
        } catch (error: Exception) {
          handler.post { failDataPath(handle, "Unable to connect data-path socket: ${error.message}") }
        }
      }
    } catch (_: RejectedExecutionException) {
      failDataPath(handle, "Socket worker capacity was exhausted")
    }
  }

  @RequiresApi(29)
  private fun startServerAccept(handle: String, resources: DataPathResources) {
    if (resources.socketTask != null) return
    val serverSocket = resources.serverSocket ?: run {
      failDataPath(handle, "Data-path server socket was unavailable")
      return
    }
    try {
      resources.socketTask = socketExecutor.submit {
        try {
          val socket = serverSocket.accept()
          handler.post {
            val active = dataPaths.path(handle)?.value
            if (active !== resources) {
              socket.close()
              return@post
            }
            resources.socket = socket
            resources.socketTask = null
            emitDataPathState(handle, "connected", "Server socket accepted client")
          }
        } catch (error: Exception) {
          if (!serverSocket.isClosed) {
            handler.post { failDataPath(handle, "Unable to accept data-path socket: ${error.message}") }
          }
        }
      }
    } catch (_: RejectedExecutionException) {
      failDataPath(handle, "Socket worker capacity was exhausted")
    }
  }

  private fun failDataPath(
    handle: String,
    reason: String,
    callbackAlreadyReleased: Boolean = false,
  ) {
    retireDataPath(handle, "failed", reason, callbackAlreadyReleased)
  }

  private fun retireDataPath(
    handle: String,
    state: String,
    reason: String,
    callbackAlreadyReleased: Boolean = false,
  ) {
    val resources = dataPaths.close(handle)?.value ?: return
    closeDataPathResources(resources, callbackAlreadyReleased)
    emitDataPathState(handle, state, reason)
  }

  private fun closeDataPathsForDiscovery(discoveryHandle: String, state: String, reason: String) {
    dataPaths.handlesForDiscovery(discoveryHandle).forEach {
      retireDataPath(it, state, reason)
    }
  }

  private fun closeDataPathResources(
    resources: DataPathResources,
    callbackAlreadyReleased: Boolean = false,
  ) {
    resources.socketTask?.cancel(true)
    resources.socketTask = null
    try { resources.socket?.close() } catch (_: Exception) {}
    resources.socket = null
    try { resources.serverSocket?.close() } catch (_: Exception) {}
    resources.serverSocket = null
    if (resources.callbackRegistered && !callbackAlreadyReleased) {
      try { resources.manager.unregisterNetworkCallback(resources.callback!!) } catch (_: IllegalArgumentException) {}
    }
    resources.callbackRegistered = false
  }

  private fun emitDataPathState(handle: String, state: String, reason: String) {
    emitOnDataPathState(Arguments.createMap().apply {
      putString("dataPathHandle", handle)
      putString("state", state)
      putString("reason", reason)
    })
  }

  @RequiresApi(26)
  private fun registerPeer(discoveryHandle: String, peer: PeerHandle): String? {
    if (handles.discoveryState(discoveryHandle) != AwareHandleRegistry.HandleState.LIVE) return null
    return peers[discoveryHandle]?.getOrPut(peer) { newHandle("peer") }
  }

  @RequiresApi(26)
  private fun rejectPendingMessagesForDiscovery(handle: String, code: String, message: String) {
    pendingMessages.removeForDiscovery(handle).forEach { it.value.reject(code, message) }
  }

  @RequiresApi(26)
  private fun manager(promise: Promise): WifiAwareManager? {
    if (!reactApplicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
      promise.reject("UNSUPPORTED", "Wi-Fi Aware is unsupported")
      return null
    }
    val manager = reactApplicationContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    if (manager == null || !manager.isAvailable) {
      promise.reject("UNAVAILABLE", "Wi-Fi Aware is unavailable")
      return null
    }
    return manager
  }

  private fun hasDiscoveryPermission(): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES
    else Manifest.permission.ACCESS_FINE_LOCATION
    return reactApplicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
  }

  private fun registerAvailabilityReceiver() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    try {
      val filter = IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        reactApplicationContext.registerReceiver(availabilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        reactApplicationContext.registerReceiver(availabilityReceiver, filter)
      }
      receiverRegistered = true
    } catch (_: RuntimeException) {
      // Operations re-check availability, so receiver registration cannot crash the host app.
    }
  }

  @RequiresApi(26)
  private fun invalidateUnavailableState() {
    if (isAwareAvailable()) return
    disposeNativeState("UNAVAILABLE", "Wi-Fi Aware became unavailable")
  }

  @RequiresApi(26)
  private fun isAwareAvailable(): Boolean =
    (reactApplicationContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager)?.isAvailable == true

  @RequiresApi(26)
  private fun disposeNativeState(code: String, message: String, emitDataPathEvents: Boolean = true) {
    pendingAttaches.values.forEach { it.reject(code, message) }
    pendingDiscoveries.values.forEach { it.second.reject(code, message) }
    pendingMessages.invalidate().forEach { it.value.reject(code, message) }
    pendingAttaches.clear(); pendingDiscoveries.clear()
    pendingDiscoverySecurityModes.clear(); discoverySecurityModes.clear()
    if (emitDataPathEvents) {
      dataPaths.handles().forEach { retireDataPath(it, "lost", message) }
    } else {
      dataPaths.invalidate().forEach { closeDataPathResources(it.value) }
    }
    val (sessions, discoveries) = handles.invalidate()
    discoveries.forEach { it.value.close() }
    peers.clear()
    sessions.forEach { it.close() }
  }

  override fun invalidate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) handler.post {
      disposeNativeState("INTERNAL_ERROR", "Wi-Fi Aware module was invalidated", emitDataPathEvents = false)
    }
    if (receiverRegistered) {
      try { reactApplicationContext.unregisterReceiver(availabilityReceiver) } catch (_: IllegalArgumentException) {}
      receiverRegistered = false
    }
    socketExecutor.shutdownNow()
    super.invalidate()
  }

  private fun newHandle(kind: String) = "$kind:${UUID.randomUUID()}"

  @RequiresApi(Build.VERSION_CODES.O)
  private fun getCapabilitiesApi26(): WritableMap {
    val supported = reactApplicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    val manager = reactApplicationContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    return capabilityMap(
      supported,
      supported && manager?.isAvailable == true,
      supported && isFrameworkOffloadedPairingSupported(manager),
    )
  }

  private fun capabilityMap(
    isSupported: Boolean,
    isAvailable: Boolean,
    isPairedDataPathSupported: Boolean,
  ): WritableMap = Arguments.createMap().apply {
    putBoolean("isSupported", isSupported)
    putBoolean("isAvailable", isAvailable)
    putBoolean("isPairedDataPathSupported", isPairedDataPathSupported)
  }

  @RequiresApi(26)
  private fun isFrameworkOffloadedPairingSupported(
    suppliedManager: WifiAwareManager? = null,
  ): Boolean {
    if (Build.VERSION.SDK_INT < 37 || Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.CINNAMON_BUN_2) {
      return false
    }
    val manager = suppliedManager
      ?: reactApplicationContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
      ?: return false
    return try {
      manager.characteristics?.isAwarePairingSupported == true
    } catch (_: SecurityException) {
      false
    } catch (_: RuntimeException) {
      false
    }
  }

  @RequiresApi(37)
  private fun enableFrameworkOffloadedPairing(config: PublishConfig.Builder) {
    config.setFrameworkOffloadedPairingEnabled(true)
  }

  @RequiresApi(37)
  private fun enableFrameworkOffloadedPairing(config: SubscribeConfig.Builder) {
    config.setFrameworkOffloadedPairingEnabled(true)
  }

  companion object {
    const val NAME = NativeWifiAwareSpec.NAME
    private const val DATA_PATH_TIMEOUT_MS = 30_000
    private const val TCP_PROTOCOL = 6
    private const val MAX_QUEUED_SOCKET_TASKS = 1
  }
}
