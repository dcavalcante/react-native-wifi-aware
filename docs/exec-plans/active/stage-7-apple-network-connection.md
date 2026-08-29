# Stage 7 — Apple network connection

Status: `PROVISIONAL — PHYSICAL VALIDATION PENDING`.

## Scope and contract

Implement the existing data-path API on iOS 26+:

```ts
openDataPath(discoverySessionHandle, peerHandle, { role, passphrase })
closeDataPath(dataPathHandle)
onDataPathState(listener)
```

`openDataPath` resolves after the native listener/connection task is registered,
not after connection readiness. It returns one opaque handle. `connected`,
`failed`, `lost`, and `closed` states are emitted asynchronously. One path per
discovery is admitted; close and all parent retirement paths are idempotent.

For Apple, `role: 'server'` requires the publisher paired-device peer emitted
by Stage 6, creates a `NetworkListener` for the selected paired device, and
uses its accepted connection for the returned path. `role: 'client'` requires
the subscriber's `WAEndpoint` peer and creates a `NetworkConnection`.
The system subscriber-picker path needs iOS 26.4 because that is when its
returned `NWEndpoint` can be converted to `WAEndpoint`; on iOS 26.0–26.3 it
rejects with `UNSUPPORTED` rather than claiming an unusable peer.

Apple pairings secure the Wi-Fi Aware link. The current API's required Android
passphrase is accepted for shared TypeScript compatibility but is not used as
an Apple PSK and must not be described as Apple transport security. The initial
Apple stack follows Apple's TLS example and defaults to bulk performance. This
stage does not expose the connection's derived `WASharedSecret` or a JS stream.

## Boundaries and non-goals

Network listener/connection objects, tasks, endpoints, TLS state, and derived
secrets remain Swift-owned. The Objective-C++ module forwards only serializable
data-path event maps. Module/discovery/session invalidation cancels every task,
clears native references, and suppresses late callbacks before any event.

No message-API port, caller-provided Apple PSK, TLS-PSK configuration, native
byte streaming, framing, files, reconnection/retry, background policy,
performance tuning, multiple peers, cross-platform interoperability, or
physical Apple claim is in scope. Stage 10 remains the optional transfer layer.

## Research decisions

| Concern | Decision |
|---|---|
| Connection roles | **Documented:** use `NetworkListener` + `WAPublisherListener` for the publisher and `NetworkConnection` from a `WAEndpoint` for the subscriber. The listener's accepted connection belongs to the pre-created server path. |
| Security | **Documented:** paired Apple Wi-Fi Aware devices have an authenticated/encrypted Wi-Fi layer; Apple demonstrates `TLS()` and provides a per-connection `WASharedSecret` for optional TLS-PSK/other higher-layer setup. **Decision:** use TLS defaults now; never misuse the Android passphrase as an Apple secret. |
| Performance | **Documented:** publisher and subscriber performance modes must match. **Decision:** leave both at Apple's default bulk mode and expose no tuning surface. |
| State/loss | **Documented:** Network listener/connection state callbacks report ready, waiting, failed, and cancellation/loss conditions. **Inference:** map ready to `connected`, unrecoverable errors to `failed`, parent/network cancellation to `lost` or `closed` according to the initiating owner. |
| Ownership | **Documented + inference:** `NetworkConnection` has task-scoped lifetime rather than `cancel()`. The data-path registry retains its owning task, whose cancellation releases the connection; one terminal event is emitted and late callbacks cannot publish. |
| Validation debt | **Physical verification required:** entitlement recognition, TLS/system pairing behavior, listener acceptance, client readiness, loss, close, and Xcode SDK compatibility require two eligible iOS/iPadOS devices. Any signing or entitlement issue is established during normal device deployment. |

Sources: [Apple connection guide](https://developer.apple.com/documentation/wifiaware/connecting-paired-devices) · [NetworkConnection](https://developer.apple.com/documentation/network/networkconnection) · [WASharedSecret](https://developer.apple.com/documentation/wifiaware/washaredsecret) · [WAPublisherListener action](https://developer.apple.com/documentation/wifiaware/wapublisherlistener/action/connecting%28to%3Afrom%3Adatapath%3A%29).

## Implementation sequence

1. Preserve the shared Codegen API; add iOS Objective-C++ forwarding to the
   Stage-6 coordinator and convert the existing Apple picker `NWEndpoint` to a
   native `WAEndpoint` before it becomes a peer handle.
2. Add a discovery-owned data-path registry with one terminal-event guard,
   task/cancellation ownership, parent cleanup, and data-path event routing.
3. Create the publisher listener from its declared service and selected paired
   device; bind its accepted connection to the returned server handle. Create a
   subscriber connection from its `WAEndpoint`; map native state updates.
4. Make the Android Stage-3 hello/message action Android-only in the example:
   the Apple server button must not call unsupported `sendMessage` after it
   registers its listener. Give the Apple harness its direct, visible flow —
   pairing, publisher listener, then subscriber client — with state and
   failure signals. Update architecture/research/roadmap/validation records
   with this flow and the passphrase/security distinction.
5. Run lint/type checks, then the smallest iOS build. Signing, install, and
   physical execution remain a later gate.

## Gate record

| Gate | State | Evidence |
|---|---|---|
| Readiness review | READY — BUILD GATE AUTHORIZED; PHYSICAL VALIDATION PENDING | Focused review verified the Apple harness flow, peer categories, passphrase semantics, accepted-path ownership, lifecycle/event routing, and current Codegen bridge accessors. Stage 6's current-generator fixes are now in the branch; Stage 7's two new bridge methods are public and the active-path error uses the existing public code. Current SDK inspection also bounds the subscriber picker conversion to iOS 26.4+. |
| Implementation | PASS | iOS coordinator owns publisher-listener/subscriber-connection records, terminal state routing, teardown, and the iOS example's pairing → listener → client flow. |
| Static validation | PASS | `yarn lint` exited 0 with eight existing/example `no-void` warnings and no errors; `yarn typecheck` exited 0 on 2026-08-25. Formatter ran for the changed TypeScript example. |
| Apple simulator compile | PASS | The current generated example passed `yarn verify:ios` on 2026-08-29. The simulator cannot validate Wi-Fi Aware behavior. |
| Apple device validation | PENDING | Pairing, listener acceptance, client readiness, loss, close, signing, and entitlement behavior require the dedicated two-device test. |

## Stop conditions

Stop and redesign if the current shared `openDataPath` contract cannot attach a
publisher accepted connection to an explicit server handle, or if the iOS SDK
requires a public TLS/security configuration absent from the contract. A later
compile or physical failure is a real pending-gate failure to diagnose, not a
reason to claim this provisional stage passed.
