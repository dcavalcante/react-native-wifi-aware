# Apple Wi-Fi Aware

**Documented.** Wi-Fi Aware was introduced for iOS/iPadOS 26. Supported hardware includes iPhone 12+ and listed recent iPads; guard OS availability and `WACapabilities`. It is not simulator-valid. [TN3111](https://developer.apple.com/documentation/technotes/tn3111-ios-wifi-api-overview) · [Framework](https://developer.apple.com/documentation/wifiaware)

Apps require `com.apple.developer.wifi-aware` with `Publish` and/or `Subscribe`, plus matching `WiFiAwareServices` declarations. Invalid/missing service role configuration can crash. Pairing is system-mediated through DeviceDiscoveryUI or AccessorySetupKit; paired devices are not reachability guarantees. [Adoption](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware) · [Entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.wifi-aware)

Wi-Fi Aware extends Network framework: listener publishes, browser discovers, connection transfers data, and errors/path details are surfaced through Network types. Retain and cancel browsers/listeners/connections deliberately. [Connections](https://developer.apple.com/documentation/wifiaware/connecting-paired-devices)

**Inference.** Native operations remain behind handles; cancellation/invalidation discards late callbacks. Apple runtime and Android↔Apple testing remain deferred until eligible physical devices exist.

## Cross-platform pairing boundary

**Documented + decision.** Apple describes Wi-Fi Aware as cross-platform. Its
system-paired link is not compatible with an Android caller-provided PSK path.
The interoperability abstraction must use Android's framework-managed paired
path when supported, normalize the shared service, and use raw TCP on that
common paired path. An Apple-only `TLS()` configuration must not be claimed
interoperable with Android's raw socket. Pairing provides Wi-Fi-layer
authentication/encryption; a later transport stage owns any end-to-end TLS or
application-encryption decision. [WWDC25 Wi-Fi Aware](https://developer.apple.com/videos/play/wwdc2025/228/)
