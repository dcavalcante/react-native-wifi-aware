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
