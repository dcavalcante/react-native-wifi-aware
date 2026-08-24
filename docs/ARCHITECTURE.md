# Architecture

The public API is capability-aware and deliberately small before 1.0. Native `WifiAwareSession`, discovery sessions, `PeerHandle`, Network framework objects, and callbacks stay native. JS receives stable opaque string handles and serializable snapshots/events only.

```ts
type AwareSessionHandle = string;
type DiscoverySessionHandle = string;
type PeerHandle = string;

getCapabilities(): CapabilitySnapshot;
attach(): Promise<AwareSessionHandle>;
closeSession(handle: AwareSessionHandle): Promise<void>;
publish(handle: AwareSessionHandle, options: { serviceName: string }): Promise<DiscoverySessionHandle>;
subscribe(handle: AwareSessionHandle, options: { serviceName: string }): Promise<DiscoverySessionHandle>;
closeDiscoverySession(handle: DiscoverySessionHandle): Promise<void>;
onPeerFound(listener): EventSubscription;
```

Stage 1 implements `getCapabilities()` on Android only: API 24-25 and Android devices without Wi-Fi Aware return both booleans as `false`; API 26+ separately reports feature support and current Aware availability. Stage 2 implements Android API-26+ attach/close and basic publish/subscribe. It is `IMPLEMENTED + CI_VERIFIED` but remains `PROVISIONAL - PHYSICAL VALIDATION PENDING` until two eligible Android devices discover each other and tear down cleanly. `onPeerFound` is subscriber-only; its peer handle is scoped to its discovery-session handle, and callbacks after closure, availability loss, or module invalidation are discarded.

The Apple bridge returns the conservative all-false capability snapshot and rejects every Stage-2 operation with `UNSUPPORTED` solely to keep the shared Codegen contract buildable; neither is an Apple hardware/runtime claim. Stage 2 exports `WifiAwareErrorCode`, `WifiAwareError`, and `isWifiAwareError`; its stable codes distinguish unsupported/unavailable service, missing discovery permission, argument/handle/session/discovery state, attach/discovery failure, and unexpected native failure. Every native handle has deterministic close/invalidate behavior; close is idempotent. Large transport/file payloads must use native sockets/streams, not JS-memory round trips.

Normalization is only accepted where semantics match. Apple pairing/declaration constraints and Android discovery sessions are exposed as platform-aware capability differences. No Android↔Apple promise exists.
