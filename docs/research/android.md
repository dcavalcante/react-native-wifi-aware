# Android Wi-Fi Aware

**Documented.** Wi-Fi Aware/NAN starts at Android 8.0 (API 26); hardware support is optional. Check `PackageManager.FEATURE_WIFI_AWARE`, then `WifiAwareManager.isAvailable()` separately. Availability can change; a runtime receiver for `ACTION_WIFI_AWARE_STATE_CHANGED` means existing sessions are unusable and must be discarded. [Overview](https://developer.android.com/develop/connectivity/wifi/wifi-aware) · [API](https://developer.android.com/reference/android/net/wifi/aware/WifiAwareManager)

Attach is asynchronous (`attach` → `WifiAwareSession`); publish/subscribe yield closeable discovery sessions. `PeerHandle` is opaque. `sendMessage` is not delivery-guaranteed; success/failure callbacks are required. Explicitly close discovery sessions, the Aware session, and unregister data-path network callbacks. Modern in-band data paths use API-29 `WifiAwareNetworkSpecifier.Builder` with `ConnectivityManager.requestNetwork`; avoid deprecated MAC/OOB APIs. Pairing is optional API 34+ capability. [DiscoverySession](https://developer.android.com/reference/android/net/wifi/aware/DiscoverySession) · [Session](https://developer.android.com/reference/android/net/wifi/aware/WifiAwareSession)

Permissions include Wi-Fi state/change state; target API 33+ requires runtime `NEARBY_WIFI_DEVICES`, with location compatibility rules documented by Android. Physical tests require two actual supported devices; feature support does not establish current availability or interoperability.

**Inference for this library.** Serialize native state transitions, invalidate handles on close/termination/availability loss, and discard late callbacks by generation.
