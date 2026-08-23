# Architecture

The public API is capability-aware and deliberately small before 1.0. Native `WifiAwareSession`, discovery sessions, `PeerHandle`, Network framework objects, and callbacks stay native. JS receives stable opaque string handles and serializable snapshots/events only.

```ts
export type CapabilitySnapshot = Readonly<{ isSupported: boolean; isAvailable: boolean }>;
export function getCapabilities(): CapabilitySnapshot;
```

Stage 1 implements `getCapabilities()` on Android only: API 24-25 and Android devices without Wi-Fi Aware return both booleans as `false`; API 26+ separately reports feature support and current Aware availability. The Apple bridge returns a conservative all-false snapshot solely to keep the shared Codegen contract buildable until the Apple implementation stage; it is not an Apple hardware/runtime claim. Discovery, messaging, pairing, and transport APIs are intentionally not exported until their platform semantics and lifecycle behavior are implemented and physically validated. Every native handle has deterministic close/invalidate behavior; close is idempotent, network callbacks are unregistered, and late callbacks are ignored. Typed stable errors cross the bridge. Large transport/file payloads must use native sockets/streams, not JS-memory round trips.

Normalization is only accepted where semantics match. Apple pairing/declaration constraints and Android discovery sessions are exposed as platform-aware capability differences. No Android↔Apple promise exists.
