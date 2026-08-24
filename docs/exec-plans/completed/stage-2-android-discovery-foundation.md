# Stage 2 — Android discovery foundation

Status: IMPLEMENTED + CI_VERIFIED — PROVISIONAL PHYSICAL VALIDATION PENDING.

Branch: `stage-2-android-discovery-foundation`.

Acceptance state: `IMPLEMENTED + CI_VERIFIED + PHYSICALLY_VERIFIED`.

## Gate record

| Gate | State | Evidence |
|---|---|---|
| Implementation | PASS | Android lifecycle/handles, Codegen event, TypeScript errors, Apple rejection stubs, and focused registry tests are present. |
| Automated validation | PASS | 2026-08-24: `yarn typecheck`, `yarn lint`, `yarn bob build`, `:react-native-wifi-aware:testDebugUnitTest`, and `yarn example build:android` passed. The example merged manifest contains all four selected permissions. |
| One-device smoke | PASS | 2026-08-24: freshly installed SM-M515F/API 31 app, with Metro reachable and `adb reverse tcp:8081 tcp:8081`, rendered `Supported: true`, `Available: true`, and `Attach/close: passed`. |
| Two-device physical discovery | PASS | 2026-08-24: SM-M515F/API 31 published; SM-X710/API 36 subscribed and displayed a peer event; both explicitly closed discovery and parent sessions. |
| Independent review | PASS | Focused final review accepted the stage and the final lifecycle delta; no BLOCKING/HIGH finding. |

## Scope and accepted contract

Implement Android API-26+ Aware attachment and basic publish/subscribe discovery. JS receives only opaque string handles; native `WifiAwareSession`, `DiscoverySession`, and `PeerHandle` objects never cross the bridge.

```ts
type AwareSessionHandle = string;
type DiscoverySessionHandle = string;
type PeerHandle = string;

attach(): Promise<AwareSessionHandle>;
closeSession(handle: AwareSessionHandle): Promise<void>;
publish(handle: AwareSessionHandle, options: { serviceName: string }): Promise<DiscoverySessionHandle>;
subscribe(handle: AwareSessionHandle, options: { serviceName: string }): Promise<DiscoverySessionHandle>;
closeDiscoverySession(handle: DiscoverySessionHandle): Promise<void>;
onPeerFound(listener: (event: { discoverySessionHandle: string; peerHandle: string }) => void): EventSubscription;
```

Handles become usable only after the corresponding native success callback. Close is idempotent local cleanup, not a hardware teardown acknowledgement. Unsupported APIs, unavailable Aware, missing runtime discovery permission, invalid/closed handles, attach/configuration failure, and unexpected native failure use stable error codes. Apple stubs remain compile-compatible and reject unsupported Stage-2 operations; this does not claim Apple runtime support.

Native promise rejection uses React Native's `Error` with a stable string `code`; the public package exports `WifiAwareErrorCode`, `WifiAwareError`, and `isWifiAwareError`. Stage 2 owns only these codes:

| Code | Condition |
|---|---|
| `UNSUPPORTED` | API below 26, missing Aware feature/manager, or an unimplemented Apple Stage-2 operation. |
| `UNAVAILABLE` | Aware is unavailable before attach or becomes unavailable while native state is live. |
| `PERMISSION_DENIED` | Required runtime discovery permission is absent. |
| `INVALID_ARGUMENT` | A service name is invalid or native configuration rejects it as invalid. |
| `INVALID_HANDLE` | A handle was never issued by this module or is the wrong handle kind. |
| `SESSION_CLOSED` | A previously issued parent session is no longer live. |
| `DISCOVERY_CLOSED` | A previously issued discovery session is no longer live. |
| `ATTACH_FAILED` | Android reports `onAttachFailed`. |
| `DISCOVERY_FAILED` | Android reports `onSessionConfigFailed`. |
| `INTERNAL_ERROR` | Unexpected native failure after defensive cleanup. |

`onPeerFound` is emitted only for Android subscribe-session `onServiceDiscovered` callbacks. Its opaque peer handle is valid only for the event's discovery-session handle. Each eligible Android callback may produce an event; the library makes no ordering, deduplication, persistence, or publisher-side peer-event promise. Closing the discovery session or parent session, native termination, availability loss, or module invalidation invalidates that peer handle and discards later callbacks without emitting another peer event.

## Research coverage and non-goals

| Concern | Decision |
|---|---|
| API support | Guard all Aware work below API 26 and reject `UNSUPPORTED` without attaching. |
| Attach | Use only `attach(AttachCallback, Handler)`; one explicit native attachment per returned JS session handle; no automatic reattach or identity-listener overload. |
| Discovery | Basic publish/subscribe with service name only; emit opaque peer handles on discovery. |
| Permissions | Add `CHANGE_WIFI_STATE`; merge `NEARBY_WIFI_DEVICES` with `neverForLocation`; cap `ACCESS_FINE_LOCATION` at API 32. JS never prompts; native rejects missing runtime permission. |
| Lifecycle | Serialize native state; close child discovery sessions before parent; invalidate on close, termination, module invalidation, or Aware availability loss; discard late callbacks by handle generation. |
| Events | Use one Codegen `EventEmitter<PeerFoundEvent>` property and a removable JS `onPeerFound` subscription. It is subscriber-only; lifecycle invalidation remains observable through typed handle errors, not a broad event API. |
| Deferred physical gate | Two Aware-capable Android devices must publish, subscribe, observe a peer event, and tear down. One-device attach/close may be additional evidence only. |

Non-goals: follow-up messages, service-specific payloads, match filters, ranging, pairing, identity/MAC exposure, discovery updates, instant communication mode, data paths, sockets, file transfer, Apple runtime implementation, and Android↔Apple behavior.

## Implementation and verification

1. Extend the Codegen spec and public TypeScript wrapper; regenerate/compile platform bindings.
2. Add a serial Android state/handle registry and focused JVM tests under `android/src/test`, using JUnit 4.13.2, for close-before-success, late callbacks, duplicate close, availability loss, and module invalidation.
3. Implement API-guarded attach, publish, subscribe, cleanup, permission checks, state-change receiver, and subscriber peer-found emission on one serialized native dispatcher. Register the receiver before re-checking availability; use `Context.RECEIVER_NOT_EXPORTED` on API 33+ and unregister it during module invalidation.
4. Add the minimal manifest permissions and update architecture/research/example only as required by the implemented contract.
5. Run typecheck, lint, Builder Bob build, Android unit tests, Android example build/install, and merged-manifest inspection.
6. If devices are attached, run one-device attach/close plus two-device publish/subscribe/teardown. Otherwise record `IMPLEMENTED + CI_VERIFIED` as `PROVISIONAL — PHYSICAL VALIDATION PENDING`.

Stop only if Codegen cannot represent the removable peer event or an Android physical result is necessary to choose the implementation contract. Missing devices alone do not stop implementation or CI validation.

## Two-device procedure

1. Install the current example on both eligible devices and start Metro; configure `adb reverse tcp:8081 tcp:8081` for each device.
2. Confirm each app displays supported/available and its automatic attach/close result.
3. On device A tap **Publish test service**; on device B tap **Subscribe test service**. Grant the requested discovery permission on each device.
4. Confirm device B displays `Peer found:`. Record any native error instead of treating an APK launch as success.
5. Tap **Close test** on both devices and confirm both display `Discovery and parent session closed.` without a crash or late peer event.
