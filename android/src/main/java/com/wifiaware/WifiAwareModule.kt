package com.wifiaware

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
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

class WifiAwareModule(reactContext: ReactApplicationContext) : NativeWifiAwareSpec(reactContext) {
  // All mutable Aware state is serialized on this handler.
  private val handler = Handler(Looper.getMainLooper())
  @RequiresApi(26) private val handles = AwareHandleRegistry<WifiAwareSession, DiscoverySession>()
  @RequiresApi(26) private val peers = mutableMapOf<String, IdentityHashMap<PeerHandle, String>>()
  @RequiresApi(26) private val pendingMessages = PendingMessageRegistry<Promise>()
  private val pendingAttaches = mutableMapOf<String, Promise>()
  private val pendingDiscoveries = mutableMapOf<String, Pair<String, Promise>>()
  private var receiverRegistered = false

  private val availabilityReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action == WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED) {
        handler.post { invalidateUnavailableState() }
      }
    }
  }

  init { registerAvailabilityReceiver() }

  override fun getCapabilities(): WritableMap {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return capabilityMap(false, false)
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
          rejectPendingMessagesForDiscovery(it, "SESSION_CLOSED", "Session closed before message completed")
          peers.remove(it)
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
    val discoveryHandle = newHandle("discovery")
    handles.beginDiscovery(discoveryHandle, parent)
    pendingDiscoveries[discoveryHandle] = parent to promise
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
      if (subscriber) session.subscribe(SubscribeConfig.Builder().setServiceName(serviceName).build(), callback, handler)
      else session.publish(PublishConfig.Builder().setServiceName(serviceName).build(), callback, handler)
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
    pendingDiscoveries.remove(handle)?.second?.resolve(handle)
  }

  @RequiresApi(26)
  private fun failDiscovery(handle: String, code: String, message: String, error: Throwable? = null) {
    handles.closeDiscovery(handle)
    peers.remove(handle)
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
    rejectPendingMessagesForDiscovery(handle, "DISCOVERY_CLOSED", "Discovery closed before message completed")
    peers.remove(handle)
    if (closeNative) record.value.close()
  }

  @RequiresApi(26)
  private fun terminateDiscovery(handle: String) {
    val record = handles.closeDiscovery(handle)
    rejectPendingMessagesForDiscovery(handle, "DISCOVERY_CLOSED", "Discovery terminated before message completed")
    peers.remove(handle)
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
  private fun disposeNativeState(code: String, message: String) {
    pendingAttaches.values.forEach { it.reject(code, message) }
    pendingDiscoveries.values.forEach { it.second.reject(code, message) }
    pendingMessages.invalidate().forEach { it.value.reject(code, message) }
    pendingAttaches.clear(); pendingDiscoveries.clear()
    val (sessions, discoveries) = handles.invalidate()
    discoveries.forEach { it.value.close() }
    peers.clear()
    sessions.forEach { it.close() }
  }

  override fun invalidate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) handler.post {
      disposeNativeState("INTERNAL_ERROR", "Wi-Fi Aware module was invalidated")
    }
    if (receiverRegistered) {
      try { reactApplicationContext.unregisterReceiver(availabilityReceiver) } catch (_: IllegalArgumentException) {}
      receiverRegistered = false
    }
    super.invalidate()
  }

  private fun newHandle(kind: String) = "$kind:${UUID.randomUUID()}"

  @RequiresApi(Build.VERSION_CODES.O)
  private fun getCapabilitiesApi26(): WritableMap {
    val supported = reactApplicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    val manager = reactApplicationContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    return capabilityMap(supported, supported && manager?.isAvailable == true)
  }

  private fun capabilityMap(isSupported: Boolean, isAvailable: Boolean): WritableMap = Arguments.createMap().apply {
    putBoolean("isSupported", isSupported)
    putBoolean("isAvailable", isAvailable)
  }

  companion object { const val NAME = NativeWifiAwareSpec.NAME }
}
