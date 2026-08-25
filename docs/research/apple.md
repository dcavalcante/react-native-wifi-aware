# Apple Wi-Fi Aware

**Documented.** Wi-Fi Aware was introduced for iOS/iPadOS 26. Supported hardware includes iPhone 12+ and listed recent iPads; guard OS availability and `WACapabilities`. It is not simulator-valid. [TN3111](https://developer.apple.com/documentation/technotes/tn3111-ios-wifi-api-overview) · [Framework](https://developer.apple.com/documentation/wifiaware)

Apps require `com.apple.developer.wifi-aware` with `Publish` and/or `Subscribe`, plus matching `WiFiAwareServices` declarations. Invalid/missing service role configuration can crash. Pairing is system-mediated through DeviceDiscoveryUI or AccessorySetupKit; paired devices are not reachability guarantees. [Adoption](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware) · [Entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.wifi-aware)

**Documented.** Apple service names are signed, static declarations rather than arbitrary runtime discovery strings. A declared service must be a fully qualified `_name._tcp` or `_name._udp` name; its name component is limited to 15 characters and invalid declarations can crash the app. The Android-only example value `com.wifiaware.stage4` is therefore a stage-scoped test identifier, not a portable library service identity. [Service declarations](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware)

**Decision required before Stage 6.** Do not silently rewrite JavaScript `serviceName` values on Apple. Choose and document either a build-time declared service registry or an explicit iOS restriction to declared service names that rejects every other value. Keep any test service neutral and stage-independent (for example, `_rn-aware._tcp` after validating Android accepts it).

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
