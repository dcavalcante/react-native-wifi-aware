# Stage 1 — Android capabilities

Status: complete.

Scope: replaced the generated placeholder with a typed capability snapshot backed by Android feature support and current Aware availability. The library manifest supplies the normal `ACCESS_WIFI_STATE` permission required by `WifiAwareManager.isAvailable()`. No runtime permission is requested and no Aware session, discovery, pairing, messaging, or network is created.

Validation: typecheck, lint, Builder Bob build, Android arm64 compile, test-app merged-manifest inspection, and a physical Android API-31 observation. The device UI displayed `Supported: true` and `Available: true` after rebuilding the native APK.

Deferred: Android API 24-25 runtime behavior and all Apple compile/runtime validation. The Apple bridge has a conservative Codegen-compatible response only; it is not an Apple Wi-Fi Aware implementation.
