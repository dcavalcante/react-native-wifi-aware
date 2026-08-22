# Architecture

The public API is capability-aware and deliberately small before 1.0. Native `WifiAwareSession`, discovery sessions, `PeerHandle`, Network framework objects, and callbacks stay native. JS receives stable opaque string handles and serializable snapshots/events only.

```ts
export type Capability = { supported: boolean; available: boolean; platform: 'android' | 'ios'; reason?: string };
export type AwareErrorCode = 'UNSUPPORTED' | 'UNAVAILABLE' | 'PERMISSION_DENIED' | 'INVALID_HANDLE' | 'CLOSED' | 'NATIVE_FAILURE';
export type AwareSession = { id: string; close(): Promise<void> };
export function getCapabilities(): Promise<Capability>;
export function attach(): Promise<AwareSession>;
```

Discovery, messaging, pairing, and transport APIs are intentionally not exported until their platform semantics and lifecycle behavior are implemented and physically validated. Every native handle has deterministic close/invalidate behavior; close is idempotent, network callbacks are unregistered, and late callbacks are ignored. Typed stable errors cross the bridge. Large transport/file payloads must use native sockets/streams, not JS-memory round trips.

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
