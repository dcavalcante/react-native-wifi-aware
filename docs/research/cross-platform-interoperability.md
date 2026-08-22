# Cross-platform Wi-Fi Aware interoperability

This feasibility record establishes the requirements for the later Stage 9
implementation. It is not physical interoperability evidence.

## Feasibility conclusion

Apple documents Wi-Fi Aware as a cross-platform technology. Android exposes
the matching framework-managed pairing primitive in API version 37.2:
`setFrameworkOffloadedPairingEnabled(true)` directs the Wi-Fi framework to pair
a peer before starting the NAN data path. This supplies the missing Android
primitive for an Apple-style paired path; it is distinct from Android's
app-provided PSK path. [Apple WWDC25 Wi-Fi Aware](https://developer.apple.com/videos/play/wwdc2025/228/)
· [Android publish pairing API](https://developer.android.com/reference/android/net/wifi/aware/PublishConfig.Builder#setFrameworkOffloadedPairingEnabled(boolean))
· [Android subscribe pairing API](https://developer.android.com/reference/android/net/wifi/aware/SubscribeConfig.Builder#setFrameworkOffloadedPairingEnabled(boolean))

The target is feasible, but no source-level conclusion substitutes for an
eligible iPhone and Android device physical test.

## Compatibility matrix

| Surface | Current Android vertical path | Current Apple vertical path | Interoperability requirement |
|---|---|---|---|
| Security | Caller supplies PSK to NDP specifier | System pairing; no app-provided PSK | Explicit `psk` versus `paired` mode; never reinterpret either credential. |
| Discovery setup | No pairing-mode option | System pairing UI and paired-peer state | Configure Android framework-offloaded pairing before requesting the path. |
| Service | Runtime `com.wifiaware.stage4` example name | Declared `_rn-aware._tcp` example name | One static Apple-declared shared service in the cross-platform harness. |
| Transport | Raw TCP socket | Apple-only `TLS()` connection | Raw TCP over the paired Wi-Fi-layer-secured link for the initial common path. |
| Capability | Local hardware/current availability | Local Apple feature snapshot | Report only local primitive support; remote pairing/connection remains asynchronous. |

The older Android PSK path remains valid for Android↔Android. It cannot make a
secure Apple path simply by reusing a service name or a passphrase. A compatible
Android 17 framework and Wi-Fi Aware hardware are required for the paired path.

## Stage 9 implementation blueprint

Stage 9 implements the already-reserved interoperability abstraction; it is not
a validation-only experiment.

1. Compile Android against an SDK containing API 37.2 and guard the paired path
   at runtime. Preserve the library's lower minimum SDK and existing PSK path.
2. Replace the mandatory universal passphrase with an explicit public union:

   ```ts
   type DataPathOptions =
     | { role: 'server' | 'client'; security: { mode: 'psk'; passphrase: string } }
     | { role: 'server' | 'client'; security: { mode: 'paired' } };
   ```

   Flatten it at the Codegen boundary to `role`, `securityMode`, and optional
   `passphrase`. iOS accepts only `paired`; Android accepts `psk` and accepts
   `paired` only where its framework and hardware support it. Every other
   combination rejects `UNSUPPORTED`.
3. Add the required paired-mode discovery option before Android requests the
   data path. Unsupported hardware/framework rejects it cleanly; it never
   silently becomes an open path, Apple-only TLS path, or PSK path.
4. Use `_rn-aware._tcp` in the generated cross-platform example. Consumer apps
   choose and statically declare their own Apple service name.
5. Use raw TCP for a paired path on both platforms. Pairing supplies Wi-Fi-layer
   authentication/encryption; end-to-end TLS, `WASharedSecret`, byte streaming,
   framing, and files remain later transport work.
6. Preserve native ownership, opaque handles, one terminal event, late-callback
   suppression, and idempotent parent teardown. Apple pairing UI presentation
   and Android framework pairing initiation are not connection success.

The library still exposes lifecycle handles rather than application byte
streams. Stage 9 establishes compatible native paths; Stage 10 owns streaming
and transfer framing.

## Validation boundary

Automated checks prove the public contract, Android compilation, and guarded
unsupported behavior. They cannot prove iPhone↔Android discovery, system
authorization, paired NDP establishment, or bidirectional transport. Physical
validation requires an eligible iPhone and Android 17/API-37.2 device, then
must visibly prove pairing, discovery, connection, loss, and teardown.

## User-provided README draft (verbatim; not yet publish-ready)

The text below is preserved exactly at the user's request. It must not replace
the public README while it describes unimplemented or physically unverified
behavior as supported.

## iPhone–Android interoperability

Apple interoperability depends on framework-managed Wi‑Fi Aware pairing, which Android introduced in version 17. Earlier releases expose a different security mechanism: their app-managed PSK data path works between Android devices, but cannot establish the secure paired connection required by an iPhone.

This creates a temporary compatibility boundary within the installed Android base:

| Device pair | Wi‑Fi Aware connection |
|---|---|
| iPhone ↔ iPhone | Supported through system pairing |
| Android ↔ Android | Supported through the mutually available Android mode |
| iPhone ↔ compatible Android 17 device | Supported through system pairing |
| iPhone ↔ Android 16 or earlier | Not interoperable; another transport is required |

Applications should handle this as transport availability, not as separate Android and iOS user populations. Keep one peer list, identity model, and user flow. When connecting two peers, request the best mutually supported Wi‑Fi Aware mode; if none exists, continue through BLE, LAN, or the Internet.

Conceptually:

```ts
const peer = await findPeer();

if (peer.supportsPairedWifiAware) {
  return connectWithPairedWifiAware(peer);
}

if (peer.supportsLegacyAndroidWifiAware) {
  return connectWithAndroidPsk(peer);
}

return connectWithFallback(peer);
```

The user should see the result—such as “Connected directly” or “Connected via Bluetooth”—rather than an operating-system compatibility decision. Platform badges and separate user pools are unnecessary.

Eligibility must be determined at runtime. The newer Android path requires the relevant framework API and Wi‑Fi Aware hardware support; checking the OS version alone is insufficient. Unsupported devices should fail capability detection cleanly so the application can select its fallback without first attempting an impossible connection.

As adoption of compatible devices grows, the same application code will select the paired path for an increasing number of mixed-platform connections. No later redesign of the peer model or UI should be necessary.

This library is therefore suitable today when Wi‑Fi Aware is an optional high-speed local transport. It is not sufficient as the only connection method for an application that must support direct communication between every iPhone and the existing Android installed base.
