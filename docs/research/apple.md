# Apple Wi-Fi Aware

**Documented.** Wi-Fi Aware was introduced for iOS/iPadOS 26. Supported hardware includes iPhone 12+ and listed recent iPads; guard OS availability and `WACapabilities`. It is not simulator-valid. [TN3111](https://developer.apple.com/documentation/technotes/tn3111-ios-wifi-api-overview) · [Framework](https://developer.apple.com/documentation/wifiaware)

Apps require `com.apple.developer.wifi-aware` with `Publish` and/or `Subscribe`, plus matching `WiFiAwareServices` declarations. Invalid/missing service role configuration can crash. Pairing is system-mediated through DeviceDiscoveryUI or AccessorySetupKit; paired devices are not reachability guarantees. [Adoption](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware) · [Entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.wifi-aware)

**Documented.** Apple service names are signed, static declarations rather than arbitrary runtime discovery strings. A declared service must be a fully qualified `_name._tcp` or `_name._udp` name; its name component is limited to 15 characters and invalid declarations can crash the app. The Android-only example value `com.wifiaware.stage4` is therefore a stage-scoped test identifier, not a portable library service identity. [Service declarations](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware)

**Decision (Stage 6).** Apple accepts a JavaScript `serviceName` only when it
exactly matches the relevant role in the host app's `WiFiAwareServices` plist;
it rejects every other value and never rewrites Android names. The library
cannot add host-app signing or plist configuration. The generated example uses
the Apple-only `_rn-aware._tcp` service and declares both roles; it does not
claim that name is valid for Android or that the platforms interoperate.

**Documented + library mapping.** DeviceDiscoveryUI presents the system pairing
flow. On Apple, `presentPairing(discoveryHandle)` resolves when the system UI is
presented, not when pairing or connection succeeds. A subscriber `onPeerFound`
event is a browser-discovered endpoint; a publisher event is an eligible paired
device. Both are discovery-scoped opaque handles and allow Stage 7's existing
client/server data-path API to remain explicit.

Wi-Fi Aware extends Network framework: listener publishes, browser discovers, connection transfers data, and errors/path details are surfaced through Network types. Retain and cancel browsers/listeners/connections deliberately. [Connections](https://developer.apple.com/documentation/wifiaware/connecting-paired-devices)

**Decision (Stage 7).** The existing shared data-path API maps a publisher's
paired-device peer to `NetworkListener` with `.selected([device])`, and a
subscriber browser/picker `WAEndpoint` peer to `NetworkConnection`. Both use
Apple's default bulk data-path settings and `TLS()` example stack. Pairing
already authenticates and encrypts the Wi-Fi link; the API's required Android
passphrase is retained for TypeScript compatibility but is deliberately not an
Apple PSK. A later transport stage may decide whether to derive the
per-connection `WASharedSecret` for a real TLS-PSK or another protocol; it is
not exposed or persisted here. The harness's Stage-3 hello/message sequence is
Android-only; Apple's visible sequence is pairing, publisher listener, then
subscriber client. [Connection guide](https://developer.apple.com/documentation/wifiaware/connecting-paired-devices) · [Shared secret](https://developer.apple.com/documentation/wifiaware/washaredsecret) · [Datapath defaults](https://developer.apple.com/documentation/wifiaware/wapublisherlistener/datapathparameters)

**Inference.** Native operations remain behind handles; cancellation/invalidation discards late callbacks. Apple runtime and Android↔Apple testing remain deferred until eligible physical devices exist.
