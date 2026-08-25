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
public final class WifiAwareCoordinator: NSObject {
  private enum DiscoveryRole {
    case publisher
    case subscriber
  }

  private final class SessionRecord {
    var closed = false
    var discoveries = Set<String>()
  }

  private final class DiscoveryRecord {
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

  @objc public static let shared = WifiAwareCoordinator()

  private let lock = NSLock()
  private var sessions = [String: SessionRecord]()
  private var discoveries = [String: DiscoveryRecord]()
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
            self.registerPeer(endpoint, for: discoveryHandle, record: record)
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

  @objc(invalidate)
  public func invalidate() {
    let records: [DiscoveryRecord]
    lock.lock()
    records = Array(discoveries.values)
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
    lock.lock()
    guard let record = discoveries[handle], !record.closed else {
      lock.unlock()
      return
    }
    record.closed = true
    record.tasks.forEach { $0.cancel() }
    record.tasks.removeAll()
    record.peers.removeAll()
    sessions[record.sessionHandle]?.discoveries.remove(handle)
    lock.unlock()

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
