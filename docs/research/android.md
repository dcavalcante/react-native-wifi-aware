# Android Wi-Fi Aware

**Documented.** Wi-Fi Aware/NAN starts at Android 8.0 (API 26); hardware support is optional. Check `PackageManager.FEATURE_WIFI_AWARE`, then `WifiAwareManager.isAvailable()` separately. Availability can change; a runtime receiver for `ACTION_WIFI_AWARE_STATE_CHANGED` means existing sessions are unusable and must be discarded. [Overview](https://developer.android.com/develop/connectivity/wifi/wifi-aware) · [API](https://developer.android.com/reference/android/net/wifi/aware/WifiAwareManager)

Attach is asynchronous (`attach` → `WifiAwareSession`); publish/subscribe yield closeable discovery sessions. `PeerHandle` is opaque. `sendMessage` is not delivery-guaranteed; success/failure callbacks are required. Explicitly close discovery sessions, the Aware session, and unregister data-path network callbacks. Modern in-band data paths use API-29 `WifiAwareNetworkSpecifier.Builder` with `ConnectivityManager.requestNetwork`; avoid deprecated MAC/OOB APIs. Pairing is optional API 34+ capability. [DiscoverySession](https://developer.android.com/reference/android/net/wifi/aware/DiscoverySession) · [Session](https://developer.android.com/reference/android/net/wifi/aware/WifiAwareSession)

**Documented.** `WifiAwareManager.isAvailable()` requires the normal manifest permission `ACCESS_WIFI_STATE`; this library declares it because its Stage 1 capability snapshot calls that API. `CHANGE_WIFI_STATE` is not required until `attach()`. The `attach(AttachCallback, Handler)` overload avoids the identity-listener location/nearby permission requirement. Publish/subscribe require the Android 13+ runtime `NEARBY_WIFI_DEVICES` permission; API 32 and lower require `ACCESS_FINE_LOCATION` for that workflow. `NEARBY_WIFI_DEVICES` should be declared with `neverForLocation` only if the library's Wi-Fi use never derives physical location. [WifiAwareManager API](https://developer.android.com/reference/android/net/wifi/aware/WifiAwareManager) · [Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)

Physical tests require two actual supported devices; feature support does not establish current availability or interoperability.

## Cross-platform paired path

**Documented.** Android API version 37.2 adds
`PublishConfig.Builder.setFrameworkOffloadedPairingEnabled` and its Subscribe
counterpart. When enabled, the framework attempts Wi-Fi Aware pairing before
the NAN data path starts; first pairing can require system authorization, and
Android recommends at least a 30-second connection timeout. This is distinct
from the API-29+ app-provided PSK/PMK data-path security configuration. The
device must support the feature at runtime. [Publish API](https://developer.android.com/reference/android/net/wifi/aware/PublishConfig.Builder#setFrameworkOffloadedPairingEnabled(boolean))
· [Subscribe API](https://developer.android.com/reference/android/net/wifi/aware/SubscribeConfig.Builder#setFrameworkOffloadedPairingEnabled(boolean))

**Decision.** The future shared contract must expose a `paired` mode guarded by
this framework capability while preserving `psk` for Android-only peers. The
mode cannot be represented by a mandatory universal passphrase, and an Android
17 label alone is insufficient: the released framework version and Wi-Fi Aware
hardware must be available on the device.

**Inference for this library.** Serialize native state transitions, invalidate handles on close/termination/availability loss, and discard late callbacks by generation.
