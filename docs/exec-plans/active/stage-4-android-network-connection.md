# Stage 4 — Android network connection

Status: COMPLETE.

## Scope

Implement one secure, point-to-point Android Wi-Fi Aware TCP data path between a
live publisher and a live subscriber. Native `Network`, `ServerSocket`, and
`Socket` objects stay native. JavaScript receives opaque data-path handles and
serializable lifecycle events only.

The public API is:

```ts
type DataPathHandle = string;
type DataPathRole = 'server' | 'client';
type DataPathOptions = Readonly<{ role: DataPathRole; passphrase: string }>;
type DataPathState = 'connected' | 'failed' | 'lost' | 'closed';
type DataPathEvent = Readonly<{
  dataPathHandle: DataPathHandle;
  state: DataPathState;
  reason: string;
}>;

openDataPath(
  discoverySessionHandle: DiscoverySessionHandle,
  peerHandle: PeerHandle,
  options: DataPathOptions
): Promise<DataPathHandle>;
closeDataPath(handle: DataPathHandle): Promise<void>;
onDataPathState(listener: (event: DataPathEvent) => void): EventSubscription;
```

`openDataPath` resolves after its native request is registered, not when the
socket connects. The Stage-3 message API signals readiness; `onDataPathState`
reports the eventual terminal or connected state.

## Boundaries and non-goals

The server must be a publisher discovery session with a subscriber peer learned
from an inbound message. The client must be a subscriber discovery session with
a publisher peer learned from discovery. Both use the same caller-provided PSK.
The server creates an ephemeral native `ServerSocket`, includes its port and TCP
protocol in a secure specifier, and accepts one socket. The client requests the
same data path without a port and opens a native socket only after its callback
provides peer address and port.

No pairing, PMK/cipher-suite/channel selection, open links, retries,
reconnection, multi-peer listeners, JavaScript byte streams, transfer framing,
files, Apple runtime behavior, or Android-to-Apple claim is in scope. Stage 10
may add an optional native streaming protocol above the owned sockets.

## Research and decisions

| Concern | Decision |
|---|---|
| API availability | **Documented:** `WifiAwareNetworkSpecifier.Builder` is API 29. Preserve library min SDK 24 and reject Stage-4 calls below API 29 with `UNSUPPORTED`. |
| Data-path sequence | **Documented:** Android's publisher-server/subscriber-client sequence uses discovery, a subscriber message, a publisher `ServerSocket`, secure network request, publisher readiness message, subscriber request, and socket creation from callback capabilities. |
| Security and port | **Documented:** a port and transport protocol are server/publisher-only and require a secure link. Require a shared passphrase for this narrow initial API. |
| Callback lifecycle | **Documented:** use `requestNetwork`; `onAvailable` precedes callback-supplied capabilities, `onUnavailable` releases the request, and `onLost` ends the satisfying network. Never synchronously query capabilities from callbacks. |
| Permissions | **Documented:** add normal `CHANGE_NETWORK_STATE` and `INTERNET`; retain existing Wi-Fi state/change and discovery permission flow. No new runtime permission is introduced. |
| Native ownership | **Inference:** keep a handler-serialized data-path registry keyed by opaque handles, retain callbacks/networks/sockets natively, close from every parent cleanup path, and discard late callbacks with a lifecycle generation. |
| Physical evidence | **Physical verification required:** secure data-path establishment, native TCP accept/connect, remote loss propagation, and teardown require the two Android devices. |

Sources: [Wi-Fi Aware overview](https://developer.android.com/develop/connectivity/wifi/wifi-aware) · [specifier builder](https://developer.android.com/reference/kotlin/android/net/wifi/aware/WifiAwareNetworkSpecifier.Builder) · [network callback](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback).

## Implementation stages

1. Extend the Codegen spec and public wrappers with the data-path handle,
   options, state event, close method, and stable `DATA_PATH_FAILED` /
   `DATA_PATH_CLOSED` errors. Add compile-only Apple rejection stubs.
2. Add the two normal manifest permissions and API-29/availability/discovery/
   peer validation. Generate Codegen before continuing native work.
3. Add a pure, JVM-testable data-path registry that maps a data path to its
   owning discovery session and gives idempotent close/invalidation semantics.
4. Implement `ConnectivityManager.requestNetwork` callbacks on the existing
   handler. Perform socket accept/connect on a bounded background executor and
   post guarded outcomes back to the handler.
5. Cascade data-path close through discovery close/termination, session close,
   availability loss, and module invalidation; unregister the callback and
   close every socket exactly once.
6. Extend the example with publisher-server, readiness, subscriber-client,
   state-log, close-data-path, and existing discovery/session teardown controls
   that remain reachable on screen.
7. Update architecture/research/README/roadmap and create one validation
   record once automated evidence exists.

## Validation and gate record

| Gate | State | Evidence |
|---|---|---|
| Readiness review | PASS | 2026-08-25: independent review returned `READY — PROVISIONAL PHYSICAL VALIDATION PENDING`; no BLOCKING/HIGH findings. |
| Implementation | PASS | Codegen/public API, Kotlin network/socket lifecycle, Apple rejection stub, a bounded one-worker socket executor, one-path-per-discovery admission, JVM registry tests, and visible role-gated example harness. Availability loss retires live paths through `lost` events; module invalidation remains intentionally silent because its JS bridge is going away. |
| Automated validation | PASS | Library Codegen, `yarn lint` (0 errors; existing `no-void` warnings), `yarn typecheck`, `yarn prepare`, focused JVM tests (4/4), and Android debug build passed on 2026-08-25. |
| Physical two-device validation | PASS | 2026-08-25: element-confirmed final Metro development build on SM-M515F (API 31) and SM-X710 (API 36). Publisher/server and subscriber/client both reached `connected`; closing the publisher emitted local `closed` and remote `lost`; both discovery and parent sessions were then explicitly closed. |
| Final review | PASS | 2026-08-25: independent final review returned `READY`; no BLOCKING or HIGH findings. It confirmed the corrected admission guard, availability-loss events, bounded executor, Codegen/Apple boundary, role-gated harness, and final two-device evidence. |

## Dependencies, risks, and stop conditions

Stage 2 and Stage 3 are physically verified on the documented API-31 and
API-36 Android devices. Stage 4 depends on those contracts only; no Apple gate
blocks it. Device availability is not an implementation stop condition, but it
does prevent a physical-validation PASS.

Stop and redesign only if Codegen cannot express the separate event contract,
or if Android evidence requires a materially different public API. A failed
automated or device gate is investigated and resolved before the stage is
reported as complete.
