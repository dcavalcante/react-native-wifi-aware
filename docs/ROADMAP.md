# Roadmap

## Validation state

`IMPLEMENTED` means the scoped code and documentation are present. `CI_VERIFIED` means its required automated, compile, and static checks passed. `PHYSICALLY_VERIFIED` means its required device checks were performed and recorded. A stage that is implemented and CI-verified but awaits a required device check is `PROVISIONAL — PHYSICAL VALIDATION PENDING`; it is not complete or fully validated.

A later stage may proceed from a provisional predecessor only when it can use the accepted contract without assuming the pending behavior was proven on hardware, records that dependency, and makes no derived physical-validation claim. It must stop when implementation depends on an unknown that only the missing physical check can resolve. That physical-validation debt propagates to dependent stages and may be discharged later by an appropriate dedicated validation pass. Release/readiness claims require every mandatory gate.

| Stage | Status | Goal | Verification / completion |
|---|---|---|---|
| 0 Contract and feasibility | complete | Research, architecture, workflow and baseline | Docs review; Android example compile; device matrix recorded |
| 1 Android capabilities | complete | Feature/availability snapshots with the manifest permission required by the Android API | Type/lint/package checks; Android compile; merged-manifest inspection; physical feature + availability observation |
| 2 Android discovery foundation | complete | Attach/close lifecycle, opaque handles, publish/subscribe, peer lifetime and teardown | Implemented, CI verified, and physically verified on two Android devices 2026-08-24. |
| 3 Android peer messaging | complete | Follow-up messages, errors/cancellation and peer/session lifetime | Implemented, CI verified, and physically verified on two Android devices 2026-08-24. |
| 4 Android network connection | complete | Secure Wi-Fi Aware data path, native socket lifecycle, loss and cleanup | CI verified and physically verified on SM-M515F (API 31) and SM-X710 (API 36) 2026-08-25; final review returned READY with no BLOCKING/HIGH findings. |
| 5 Android physical validation pass | planned | Cumulative Android regression evidence for completed provisional work | Recorded available-device matrix and regression notes; not a feature implementation milestone |
| 6 Apple discovery foundation | provisional — physical validation pending | iOS-26 capability/configuration, host-declared services, pairing, paired peers, and subscriber browser lifecycle | The current generated example passed the iOS simulator compile on 2026-08-29. The simulator cannot validate Wi-Fi Aware; two-device Apple validation remains pending. |
| 7 Apple network connection | provisional — physical validation pending | Network framework publisher listener, subscriber connection, data-path lifecycle, and Apple-specific harness flow | The current generated example passed the iOS simulator compile on 2026-08-29 after correcting the current NetworkConnection lifecycle API. Two-device Apple validation remains pending. |
| 8 Apple physical validation | deferred | Entitlement, pairing, discovery and connection tests | Eligible physical Apple devices |
| 9 Cross-platform interoperability abstraction | provisional — physical validation pending | Android 17.2/API-37.2 paired mode, explicit PSK/paired contract, shared service identity, and compatible raw-TCP path | Android and iOS compile checks passed 2026-09-01; iPhone↔eligible-Android physical evidence remains required |
| 10 Transport/file work | planned | Optional native streaming protocol above platform primitives, not JS bulk copying | Automated transfer checks and physical throughput/loss tests |

Android and Apple implementation tracks are independently schedulable. A stage depends only on relevant prior platform or shared-contract guarantees; it does not inherit an unrelated platform's pending physical-validation debt.

Stage 9 is implementation work followed by a physical validation gate. It owns
the missing compatibility abstraction identified by the feasibility baseline;
it is not a catch-all experiment and it is not merely a device test.

Each stage excludes unlisted transport/features, depends on preceding lifecycle guarantees, runs generated checks plus focused tests, and does not advance on unreviewed BLOCKING/HIGH findings. Unavailable Apple hardware defers only stages 8–9 and Apple runtime claims.
