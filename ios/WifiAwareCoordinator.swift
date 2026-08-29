import DeviceDiscoveryUI
import Foundation
import Network
import React
import UIKit
import WiFiAware

/**
 * iOS 26 Wi-Fi Aware state owner. The Objective-C++ TurboModule only forwards
 * serializable values here; Apple services, paired devices, endpoints, and
 * system controllers never leave native code.
 */
@objc(WifiAwareCoordinator)
public final class WifiAwareCoordinator: NSObject, @unchecked Sendable {
  private enum DiscoveryRole: Equatable {
    case publisher
    case subscriber
  }

  private final class SessionRecord {
    var closed = false
    var discoveries = Set<String>()
  }

  private final class DiscoveryRecord: @unchecked Sendable {
    let sessionHandle: String
    let role: DiscoveryRole
    let serviceName: String
    var closed = false
    var peers = [String: Any]()
    var tasks = [Task<Void, Never>]()
    weak var pairingController: UIViewController?

    init(sessionHandle: String, role: DiscoveryRole, serviceName: String) {
      self.sessionHandle = sessionHandle
      self.role = role
      self.serviceName = serviceName
    }
  }

  private final class DataPathRecord: @unchecked Sendable {
    let discoveryHandle: String
    let role: String
    var terminal = false
    var connected = false
    var acceptedConnection = false
    var task: Task<Void, Never>?

    init(discoveryHandle: String, role: String) {
      self.discoveryHandle = discoveryHandle
      self.role = role
    }
  }

  @objc public static let shared = WifiAwareCoordinator()

  private let lock = NSLock()
  private var sessions = [String: SessionRecord]()
  private var discoveries = [String: DiscoveryRecord]()
  private var dataPaths = [String: DataPathRecord]()
  private var eventSink: ((NSDictionary) -> Void)?

  @objc(setEventSink:)
  public func setEventSink(_ sink: @escaping (NSDictionary) -> Void) {
    lock.lock()
    eventSink = sink
    lock.unlock()
  }

  @objc(capabilities)
  public func capabilities() -> NSDictionary {
    guard #available(iOS 26.0, *) else {
      return ["isSupported": false, "isAvailable": false]
    }
    let supported = WACapabilities.supportedFeatures.contains(.wifiAware)
    // Apple exposes supported features, not Android's mutable service
    // availability snapshot. Individual operations surface runtime failures.
    return ["isSupported": supported, "isAvailable": supported]
  }

  @objc(attachWithResolve:reject:)
  public func attach(
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard supported else {
      reject("UNSUPPORTED", "Wi-Fi Aware requires iOS 26 on supported hardware", nil)
      return
    }
    let handle = makeHandle("aware")
    lock.lock()
    sessions[handle] = SessionRecord()
    lock.unlock()
    resolve(handle)
  }

  @objc(closeSessionWithHandle:resolve:reject:)
  public func closeSession(
    handle: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    let childHandles: [String]
    lock.lock()
    guard let session = sessions[handle] else {
      lock.unlock()
      reject("INVALID_HANDLE", "Unknown session handle", nil)
      return
    }
    if session.closed {
      lock.unlock()
      resolve(nil)
      return
    }
    session.closed = true
    childHandles = Array(session.discoveries)
    lock.unlock()

    childHandles.forEach(closeDiscovery)
    resolve(nil)
  }

  @objc(publishWithSessionHandle:serviceName:resolve:reject:)
  public func publish(
    sessionHandle: String,
    serviceName: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    startDiscovery(
      sessionHandle: sessionHandle,
      serviceName: serviceName,
      role: .publisher,
      resolve: resolve,
      reject: reject
    )
  }

  @objc(subscribeWithSessionHandle:serviceName:resolve:reject:)
  public func subscribe(
    sessionHandle: String,
    serviceName: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    startDiscovery(
      sessionHandle: sessionHandle,
      serviceName: serviceName,
      role: .subscriber,
      resolve: resolve,
      reject: reject
    )
  }

  @objc(presentPairingWithDiscoveryHandle:resolve:reject:)
  public func presentPairing(
    discoveryHandle: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard #available(iOS 26.0, *) else {
      reject("UNSUPPORTED", "Wi-Fi Aware requires iOS 26", nil)
      return
    }
    guard let record = liveDiscovery(discoveryHandle) else {
      reject(discoveryError(discoveryHandle), "Discovery session is not live", nil)
      return
    }

    DispatchQueue.main.async { [weak self, weak record] in
      guard let self, let record, !record.closed else {
        reject("DISCOVERY_CLOSED", "Discovery closed before pairing UI was presented", nil)
        return
      }
      guard let presenter = self.presentingViewController() else {
        reject("INTERNAL_ERROR", "Unable to find a view controller for pairing", nil)
        return
      }

      switch record.role {
      case .publisher:
        guard let service = WAPublishableService.allServices[record.serviceName] else {
          reject("INVALID_ARGUMENT", "Service is not declared publishable in WiFiAwareServices", nil)
          return
        }
        let provider = WAPublisherListener.wifiAware(
          .connecting(to: service, from: .userSpecifiedDevices)
        )
        let controller = DDDevicePairingViewController(
          listenerProvider: provider,
          access: .permanent
        )
        controller.modalPresentationStyle = .fullScreen
        record.pairingController = controller
        presenter.present(controller, animated: true) { resolve(nil) }
        self.observePairedDevices(for: discoveryHandle)

      case .subscriber:
        guard #available(iOS 26.4, *) else {
          reject("UNSUPPORTED", "Apple device selection requires iOS 26.4 or later", nil)
          return
        }
        guard let service = WASubscribableService.allServices[record.serviceName] else {
          reject("INVALID_ARGUMENT", "Service is not declared subscribable in WiFiAwareServices", nil)
          return
        }
        let provider = WASubscriberBrowser.wifiAware(
          .connecting(to: .userSpecifiedDevices, from: service)
        )
        guard let controller = DDDevicePickerViewController(
          browseDescriptor: provider.makeDescriptor(),
          parameters: provider.configureParameters(nil),
          access: .permanent
        ) else {
          reject("UNSUPPORTED", "System device picker is unavailable", nil)
          return
        }
        controller.modalPresentationStyle = .fullScreen
        record.pairingController = controller
        presenter.present(controller, animated: true) { resolve(nil) }
        Task { [weak self, weak record] in
          do {
            let endpoint = try await controller.endpoint
            guard let self, let record, !record.closed else { return }
            guard let awareEndpoint = endpoint.wifiAware else { return }
            self.registerPeer(awareEndpoint, for: discoveryHandle, record: record)
          } catch {
            // Cancellation is a user action, not a failed discovery session.
          }
        }
      }
    }
  }

  @objc(closeDiscoveryWithHandle:resolve:reject:)
  public func closeDiscovery(
    handle: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    lock.lock()
    guard discoveries[handle] != nil else {
      lock.unlock()
      reject("INVALID_HANDLE", "Unknown discovery handle", nil)
      return
    }
    lock.unlock()
    closeDiscovery(handle)
    resolve(nil)
  }

  @objc(openDataPathWithDiscoveryHandle:peerHandle:role:passphrase:resolve:reject:)
  public func openDataPath(
    discoveryHandle: String,
    peerHandle: String,
    role: String,
    passphrase: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard #available(iOS 26.0, *) else {
      reject("UNSUPPORTED", "Wi-Fi Aware data paths require iOS 26", nil)
      return
    }
    guard role == "server" || role == "client" else {
      reject("INVALID_ARGUMENT", "Data-path role must be server or client", nil)
      return
    }
    guard !passphrase.isEmpty else {
      reject("INVALID_ARGUMENT", "A non-empty passphrase is required by the shared API", nil)
      return
    }

    let handle = makeHandle("data-path")
    let dataPath = DataPathRecord(discoveryHandle: discoveryHandle, role: role)
    let peer: Any
    let serviceName: String

    lock.lock()
    guard let discovery = discoveries[discoveryHandle] else {
      lock.unlock()
      reject("INVALID_HANDLE", "Unknown discovery handle", nil)
      return
    }
    guard !discovery.closed else {
      lock.unlock()
      reject("DISCOVERY_CLOSED", "Discovery session is not live", nil)
      return
    }
    guard discovery.role == (role == "server" ? .publisher : .subscriber) else {
      lock.unlock()
      reject("INVALID_ARGUMENT", "Server paths require publish; client paths require subscribe", nil)
      return
    }
    guard let storedPeer = discovery.peers[peerHandle] else {
      lock.unlock()
      reject("INVALID_HANDLE", "Peer does not belong to the discovery session", nil)
      return
    }
    guard !dataPaths.values.contains(where: { $0.discoveryHandle == discoveryHandle && !$0.terminal }) else {
      lock.unlock()
      reject("DATA_PATH_FAILED", "A data path is already active for this discovery session", nil)
      return
    }
    peer = storedPeer
    serviceName = discovery.serviceName
    dataPaths[handle] = dataPath
    lock.unlock()

    switch (role, peer) {
    case ("server", let device as WAPairedDevice):
      dataPath.task = startServerDataPath(
        handle: handle,
        record: dataPath,
        serviceName: serviceName,
        device: device
      )
    case ("client", let endpoint as WAEndpoint):
      dataPath.task = startClientDataPath(handle: handle, record: dataPath, endpoint: endpoint)
    default:
      removeUnstartedDataPath(handle, record: dataPath)
      reject(
        "INVALID_ARGUMENT",
        role == "server"
          ? "Publisher data paths require a paired-device peer from Apple pairing"
          : "Subscriber data paths require a Wi-Fi Aware endpoint peer",
        nil
      )
      return
    }
    resolve(handle)
  }

  @objc(closeDataPathWithHandle:resolve:reject:)
  public func closeDataPath(
    handle: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    lock.lock()
    let path = dataPaths[handle]
    lock.unlock()
    guard let path else {
      reject("INVALID_HANDLE", "Unknown data-path handle", nil)
      return
    }
    retireDataPath(handle, record: path, state: "closed", reason: "Data path closed")
    resolve(nil)
  }

  @objc(invalidate)
  public func invalidate() {
    let records: [DiscoveryRecord]
    let paths: [DataPathRecord]
    lock.lock()
    records = Array(discoveries.values)
    paths = Array(dataPaths.values)
    paths.forEach { $0.terminal = true }
    dataPaths.removeAll()
    records.forEach {
      $0.closed = true
      $0.tasks.forEach { $0.cancel() }
      $0.tasks.removeAll()
      $0.peers.removeAll()
    }
    sessions.removeAll()
    discoveries.removeAll()
    eventSink = nil
    lock.unlock()

    paths.forEach {
      $0.task?.cancel()
    }

    DispatchQueue.main.async {
      records.forEach { $0.pairingController?.dismiss(animated: false) }
    }
  }

  private var supported: Bool {
    guard #available(iOS 26.0, *) else { return false }
    return WACapabilities.supportedFeatures.contains(.wifiAware)
  }

  private func startDiscovery(
    sessionHandle: String,
    serviceName: String,
    role: DiscoveryRole,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard supported else {
      reject("UNSUPPORTED", "Wi-Fi Aware requires iOS 26 on supported hardware", nil)
      return
    }
    guard !serviceName.isEmpty else {
      reject("INVALID_ARGUMENT", "A non-empty serviceName is required", nil)
      return
    }
    guard #available(iOS 26.0, *) else {
      reject("UNSUPPORTED", "Wi-Fi Aware requires iOS 26", nil)
      return
    }

    let declared = switch role {
    case .publisher: WAPublishableService.allServices[serviceName] != nil
    case .subscriber: WASubscribableService.allServices[serviceName] != nil
    }
    guard declared else {
      reject(
        "INVALID_ARGUMENT",
        "serviceName must exactly match a host-declared service with the required Apple role",
        nil
      )
      return
    }

    let handle = makeHandle("discovery")
    let record = DiscoveryRecord(sessionHandle: sessionHandle, role: role, serviceName: serviceName)
    lock.lock()
    guard let session = sessions[sessionHandle] else {
      lock.unlock()
      reject("INVALID_HANDLE", "Unknown session handle", nil)
      return
    }
    guard !session.closed else {
      lock.unlock()
      reject("SESSION_CLOSED", "Session is not live", nil)
      return
    }
    sessions[sessionHandle]?.discoveries.insert(handle)
    discoveries[handle] = record
    lock.unlock()

    if role == .subscriber {
      startBrowser(for: handle, record: record)
    }
    resolve(handle)
  }

  @available(iOS 26.0, *)
  private func startBrowser(for discoveryHandle: String, record: DiscoveryRecord) {
    guard let service = WASubscribableService.allServices[record.serviceName] else { return }
    let provider = WASubscriberBrowser.wifiAware(
      .connecting(to: .allPairedDevices, from: service)
    )
    let browser = NetworkBrowser(for: provider)
    let task = Task { [weak self, weak record] in
      do {
        let endpoint: WAEndpoint = try await browser.run { endpoints in
          guard let endpoint = endpoints.first else { return .continue }
          return .finish(endpoint)
        }
        DispatchQueue.main.async {
          guard let self, let record, !record.closed else { return }
          self.registerPeer(endpoint, for: discoveryHandle, record: record)
        }
      } catch {
        // The public contract has no discovery-state event. Closing a parent
        // invalidates this task; physical validation will determine whether a
        // stable surface for runtime browser errors is needed.
      }
    }
    record.tasks.append(task)
  }

  @available(iOS 26.0, *)
  private func observePairedDevices(for discoveryHandle: String) {
    guard let record = liveDiscovery(discoveryHandle), record.role == .publisher else { return }
    let task = Task { [weak self, weak record] in
      do {
        for try await devices in WAPairedDevice.allDevices {
          DispatchQueue.main.async {
            guard let self, let record, !record.closed else { return }
            devices.values.forEach { self.registerPeer($0, for: discoveryHandle, record: record) }
          }
        }
      } catch {
        // A later physical gate distinguishes a denied entitlement from a
        // transient paired-device change; no peer event is emitted on failure.
      }
    }
    record.tasks.append(task)
  }

  @available(iOS 26.0, *)
  private func startServerDataPath(
    handle: String,
    record: DataPathRecord,
    serviceName: String,
    device: WAPairedDevice
  ) -> Task<Void, Never> {
    Task { [weak self, weak record] in
      guard let service = WAPublishableService.allServices[serviceName] else {
        self?.retireDataPath(
          handle,
          record: record,
          state: "failed",
          reason: "Publishable service is no longer declared by the host"
        )
        return
      }

      do {
        let listener = try NetworkListener(
          for: .wifiAware(.connecting(to: service, from: .selected([device]))),
          using: { TLS() }
        ).onStateUpdate { [weak self, weak record] _, state in
          switch state {
          case .failed(let error):
            self?.retireDataPath(
              handle,
              record: record,
              state: "failed",
              reason: "Publisher listener failed: \(error)"
            )
          case .cancelled:
            self?.retireDataPath(
              handle,
              record: record,
              state: "lost",
              reason: "Publisher listener was cancelled"
            )
          case .setup, .waiting, .ready:
            break
          @unknown default:
            break
          }
        }

        try await listener.run { [weak self, weak record] connection in
          guard let self, let record,
                self.bindAcceptedConnection(handle: handle, record: record)
          else {
            return
          }
          connection.onStateUpdate { [weak self, weak record] _, state in
            self?.handleConnectionState(state, handle: handle, record: record)
          }
          try await Self.waitForTaskCancellation()
        }
      } catch is CancellationError {
        // Explicit close, parent teardown, and module invalidation are silent
        // after their terminal state has been recorded.
      } catch {
        self?.retireDataPath(
          handle,
          record: record,
          state: "failed",
          reason: "Unable to start publisher listener: \(error)"
        )
      }
    }
  }

  @available(iOS 26.0, *)
  private func startClientDataPath(
    handle: String,
    record: DataPathRecord,
    endpoint: WAEndpoint
  ) -> Task<Void, Never> {
    Task { [weak self, weak record] in
      do {
        // WAEndpoint is a Connectable, so it can create the typed Wi-Fi Aware
        // NetworkConnection directly. Keep it in this task's scope; the new
        // Network API closes it when this task ends or is cancelled.
        let connection = NetworkConnection(to: endpoint, using: { TLS() })
        connection.onStateUpdate { [weak self, weak record] _, state in
          self?.handleConnectionState(state, handle: handle, record: record)
        }
        try await Self.waitForTaskCancellation()
      } catch is CancellationError {
        // Explicit close, parent teardown, and module invalidation end the
        // task, which releases the NetworkConnection and closes it.
      } catch {
        self?.retireDataPath(
          handle,
          record: record,
          state: "failed",
          reason: "Unable to start subscriber connection: \(error)"
        )
      }
    }
  }

  @available(iOS 26.0, *)
  private static func waitForTaskCancellation() async throws {
    // NetworkConnection has no cancel() API. Apple defines its lifetime by the
    // task that owns it, so a data-path close cancels this suspension and drops
    // the final connection reference.
    try await Task.sleep(nanoseconds: UInt64.max)
  }

  @available(iOS 26.0, *)
  private func handleConnectionState(
    _ state: NetworkChannel<TLS>.State,
    handle: String,
    record: DataPathRecord?
  ) {
    switch state {
    case .ready:
      emitConnected(handle, record: record)
    case .failed(let error):
      retireDataPath(
        handle,
        record: record,
        state: "failed",
        reason: "Data-path connection failed: \(error)"
      )
    case .cancelled:
      retireDataPath(
        handle,
        record: record,
        state: "lost",
        reason: "Data-path connection was cancelled"
      )
    case .setup, .preparing, .waiting:
      break
    @unknown default:
      break
    }
  }

  private func bindAcceptedConnection(
    handle: String,
    record: DataPathRecord
  ) -> Bool {
    lock.lock()
    guard dataPaths[handle] === record, !record.terminal, !record.acceptedConnection else {
      lock.unlock()
      return false
    }
    record.acceptedConnection = true
    lock.unlock()
    return true
  }

  private func emitConnected(_ handle: String, record: DataPathRecord?) {
    guard let record else { return }
    lock.lock()
    guard dataPaths[handle] === record, !record.terminal, !record.connected else {
      lock.unlock()
      return
    }
    record.connected = true
    let sink = eventSink
    lock.unlock()
    sink?([
      "eventName": "onDataPathState",
      "dataPathHandle": handle,
      "state": "connected",
      "reason": record.role == "server" ? "Server accepted client" : "Client connected",
    ])
  }

  private func retireDataPath(
    _ handle: String,
    record: DataPathRecord?,
    state: String,
    reason: String
  ) {
    guard let record else { return }
    let task: Task<Void, Never>?
    let sink: ((NSDictionary) -> Void)?
    lock.lock()
    guard dataPaths[handle] === record, !record.terminal else {
      lock.unlock()
      return
    }
    record.terminal = true
    task = record.task
    record.task = nil
    sink = eventSink
    lock.unlock()

    task?.cancel()
    sink?([
      "eventName": "onDataPathState",
      "dataPathHandle": handle,
      "state": state,
      "reason": reason,
    ])
  }

  private func closeDataPaths(for discoveryHandle: String, state: String, reason: String) {
    lock.lock()
    let paths = dataPaths.filter {
      $0.value.discoveryHandle == discoveryHandle && !$0.value.terminal
    }
    lock.unlock()
    paths.forEach { handle, record in
      retireDataPath(handle, record: record, state: state, reason: reason)
    }
  }

  private func removeUnstartedDataPath(_ handle: String, record: DataPathRecord) {
    lock.lock()
    guard dataPaths[handle] === record else {
      lock.unlock()
      return
    }
    dataPaths.removeValue(forKey: handle)
    lock.unlock()
  }

  private func registerPeer(_ peer: Any, for discoveryHandle: String, record: DiscoveryRecord) {
    let handle = makeHandle("peer")
    lock.lock()
    guard discoveries[discoveryHandle] === record, !record.closed else {
      lock.unlock()
      return
    }
    if record.peers.values.contains(where: { String(describing: $0) == String(describing: peer) }) {
      lock.unlock()
      return
    }
    record.peers[handle] = peer
    let sink = eventSink
    lock.unlock()

    sink?([
      "eventType": "peerFound",
      "discoverySessionHandle": discoveryHandle,
      "peerHandle": handle,
      "payload": [],
    ])
  }

  private func closeDiscovery(_ handle: String) {
    let tasks: [Task<Void, Never>]
    lock.lock()
    guard let record = discoveries[handle], !record.closed else {
      lock.unlock()
      return
    }
    record.closed = true
    tasks = record.tasks
    record.tasks.removeAll()
    record.peers.removeAll()
    sessions[record.sessionHandle]?.discoveries.remove(handle)
    lock.unlock()

    closeDataPaths(for: handle, state: "closed", reason: "Parent discovery closed")
    tasks.forEach { $0.cancel() }

    DispatchQueue.main.async {
      record.pairingController?.dismiss(animated: true)
    }
  }

  private func liveDiscovery(_ handle: String) -> DiscoveryRecord? {
    lock.lock()
    defer { lock.unlock() }
    guard let record = discoveries[handle], !record.closed else { return nil }
    return record
  }

  private func discoveryError(_ handle: String) -> String {
    lock.lock()
    defer { lock.unlock() }
    return discoveries[handle] == nil ? "INVALID_HANDLE" : "DISCOVERY_CLOSED"
  }

  private func presentingViewController() -> UIViewController? {
    UIApplication.shared.connectedScenes
      .compactMap { ($0 as? UIWindowScene)?.keyWindow }
      .first?
      .rootViewController?
      .presentedOrSelf
  }

  private func makeHandle(_ kind: String) -> String {
    "\(kind):\(UUID().uuidString.lowercased())"
  }
}

private extension UIViewController {
  var presentedOrSelf: UIViewController {
    presentedViewController?.presentedOrSelf ?? self
  }
}
