# Stage 9 — Cross-platform interoperability abstraction

Status: `IMPLEMENTED — READY — PROVISIONAL PHYSICAL VALIDATION PENDING`.

## Scope

Establish one explicit paired Wi-Fi Aware path that can be exercised between an
eligible Android 17.2 device and an Apple device. Preserve the existing Android
PSK path for Android↔Android; do not make it appear Apple-compatible.

| Surface | Stage 9 decision |
|---|---|
| Discovery/service | The example uses `_rn-aware._tcp` on both platforms. Consumer Apple apps must statically declare their own service in `WiFiAwareServices`. Discovery records retain the selected security mode. |
| Security/pairing | Public `DataPathOptions` becomes a `psk` / `paired` union. The Codegen boundary is flattened to `role`, `securityMode`, and optional `passphrase`. `paired` is required on Apple and is enabled on Android discovery using framework-offloaded pairing; `psk` remains Android-only. |
| Data path | The Android paired path uses the framework-managed pairing configured before NDP setup. Apple and Android use raw TCP for this common primitive; the current Apple TLS configuration is removed from this path. |
| Capability/error | Local support only: Apple accepts `paired`; Android accepts `psk`, and accepts `paired` only with Android 17.2 API availability plus Wi-Fi Aware pairing hardware support. Unsupported combinations reject `UNSUPPORTED`; no remote-device outcome is promised. |
| Lifecycle | Reuse opaque discovery/data-path handles, one-terminal-event guard, idempotent close, parent teardown, and late-callback suppression. Pairing initiation is not connection success. |

## Non-goals

- No JavaScript byte stream, framing, files, TLS/PSK configuration, or
  `WASharedSecret` exposure; those remain Stage 10.
- No claim that Android↔Apple works until the physical gate passes.
- No conversion of a legacy Android PSK path into a paired path, and no
  Apple-side support for caller-provided passphrases.
- No backward compatibility promise for the pre-release mandatory-passphrase
  API; this stage intentionally replaces it with explicit semantics.

## Dependencies and risks

- **Compile dependency — SATISFIED:** local Android SDK platform 37.2 contains
  `setFrameworkOffloadedPairingEnabled`; the library targets that SDK minor and
  compiled with JDK 17. This is distinct from runtime support.
- **Runtime dependency — PENDING:** an Android 17.2 framework with Wi-Fi Aware
  pairing support, plus eligible Apple hardware, is needed for the physical
  interoperability gate.
- **Apple parameter boundary — SATISFIED:** `TCP()` raw transport parameters
  compile in the current Network framework path. The paired Wi-Fi layer remains
  the security boundary; task ownership and cancellation behavior are unchanged.
- **Harness:** the shipped example must visibly select paired mode, show local
  unsupported/failure states, and make publish, subscribe, pairing, open,
  close, and terminal state actions accessible for the cross-device procedure.

## Implementation sequence

1. Install/verify the Android 37.2 compile API and JDK toolchain; set the
   library build target to the smallest SDK that exposes the method while
   retaining the current minimum SDK. Confirm the method is present in the
   installed `android.jar` before source edits depend on it.
2. Change the TypeScript public union and flat Codegen spec; regenerate and
   adapt Android and Objective-C++ forwarding. Validate malformed modes and
   mode/passphrase combinations in JS before native calls.
3. Add explicit discovery security mode. On Android, configure
   `setFrameworkOffloadedPairingEnabled(true)` on publish/subscribe only for
   `paired`, with a runtime/full-version and characteristics guard. Store the
   mode and require it to agree with `openDataPath`. On iOS, reject `psk` and
   retain the existing system-pairing peer constraints for `paired`.
4. Build Android PSK and paired network specifiers without cross-contaminating
   credentials. Increase the paired request timeout to at least 30 seconds as
   required by the Android API documentation; preserve connection-loss and
   teardown behavior.
5. Replace the Apple Stage 7 TLS parameters with the documented raw-TCP
   parameters for this paired primitive, preserving task-scoped connection
   ownership and typed callback handling.
6. Convert the example to `_rn-aware._tcp`, paired mode, and a clear physical
   cross-platform harness. Update public README, architecture, research,
   roadmap, and one Stage 9 validation record to distinguish implemented,
   compile-verified, unsupported, and physically unvalidated combinations.
7. Validate: targeted TypeScript tests/static checks; Android compile and
   guarded unsupported behavior; `yarn verify:ios`; then an iPhone↔eligible
   Android device run that visibly proves pairing, discovery, bidirectional
   connection readiness, close, loss, and parent teardown.

## Gate record

| Gate | State | Evidence |
|---|---|---|
| Existing feasibility | PASS | `docs/research/cross-platform-interoperability.md` records the public Android 37.2 pairing API, Apple paired path, common service, and physical debt. |
| Readiness review | PASS | Independent focused review returned `READY — PROVISIONAL PHYSICAL VALIDATION PENDING` on 2026-09-01; no BLOCKING or HIGH findings. |
| Android compile dependency | PASS | SDK 37.2 `android.jar` contains the paired APIs; the new Android example bundle compiled successfully with JDK 17. |
| Implementation | PASS | Public contract, Android paired discovery/NDP, Apple raw TCP, example harness, and documentation are implemented. |
| Automated validation | PASS | `yarn typecheck`, `yarn lint` (warnings only), Android `app:bundleDebug`, and `yarn verify:ios` passed on 2026-09-01. |
| Physical cross-platform validation | PENDING | Requires eligible iPhone and Android 17.2 Wi-Fi Aware pairing hardware. |

## Stop conditions

Stop and redesign if the Android public API requires a pairing configuration
that cannot be represented without exposing incompatible credentials, if the
Apple Network framework has no raw-TCP form for paired endpoints, or if either
platform cannot retain the selected security mode through discovery and
data-path teardown. A missing local SDK or missing eligible devices is an
external dependency, not evidence that the abstraction works.
