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

Before a cross-platform data path is exported, its shared contract must express
the actual pairing/security mode and common transport rather than flattening a
platform-specific credential into a universal field. The later interoperability
abstraction stage owns this normalization and a capability/error result that is
truthful about local support without predicting remote-device success.

That contract has two intentionally distinct modes: Android's existing
app-managed `psk` path, and a `paired` path negotiated by the operating system.
Apple accepts only `paired`; Android can expose it only where the framework
paired API is available. The common service identity and raw TCP path belong to
the same abstraction. Application-level streaming or TLS is explicitly later
transport work, not a claim made by the Wi-Fi Aware primitive.
