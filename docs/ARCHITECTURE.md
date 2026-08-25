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
presentPairing(discoverySessionHandle: DiscoverySessionHandle): Promise<void>;
closeDiscoverySession(handle: DiscoverySessionHandle): Promise<void>;
onPeerFound(listener): EventSubscription;
sendMessage(discoverySessionHandle: DiscoverySessionHandle, peerHandle: PeerHandle, payload: ReadonlyArray<number>): Promise<void>;
onMessageReceived(listener): EventSubscription;
```

Stage 1 implements `getCapabilities()` on Android only: API 24-25 and Android devices without Wi-Fi Aware return both booleans as `false`; API 26+ separately reports feature support and current Aware availability. Stage 2 implements Android API-26+ attach/close and basic publish/subscribe. It is `IMPLEMENTED + CI_VERIFIED + PHYSICALLY_VERIFIED`: two eligible Android devices discovered each other and tore down cleanly on 2026-08-24. `onPeerFound` is subscriber-only; its peer handle is scoped to its discovery-session handle, and callbacks after closure, availability loss, or module invalidation are discarded.

Stage 2 exports `WifiAwareErrorCode`, `WifiAwareError`, and `isWifiAwareError`; its stable codes distinguish unsupported/unavailable service, missing discovery permission, argument/handle/session/discovery state, attach/discovery failure, and unexpected native failure. Every native handle has deterministic close/invalidate behavior; close is idempotent. Large transport/file payloads must use native sockets/streams, not JS-memory round trips.

Stage 3 adds Android follow-up messaging only: `sendMessage` accepts a small read-only byte array, resolves after Android confirms the send, and rejects with `MESSAGE_SEND_FAILED` when Android reports failure. `onMessageReceived` carries a serializable byte array and an opaque peer handle. The peer is valid only for the emitted discovery handle. Pending sends are rejected on close, termination, availability loss, or module invalidation; later callbacks are ignored. Android does not guarantee ordering, deduplication, or retry behavior, and this API does not add those guarantees.

Stage 4 adds Android API-29+ secure point-to-point data paths. `openDataPath` returns an opaque handle after registering a publisher-server or subscriber-client network request; `onDataPathState` reports native socket connection, failure, loss, or closure. The `Network`, addresses, ports, `ServerSocket`, and `Socket` remain native. A discovery/session close, availability loss, or module invalidation unregisters callbacks and closes child data paths before native discovery/session resources. This is not a JavaScript streaming or file-transfer API.

Stage 6 provisionally implements Apple iOS-26 discovery configuration and system
pairing. The host application must sign the `Publish`/`Subscribe` entitlement
and statically declare each service in `WiFiAwareServices`; Apple calls reject
an undeclared or role-mismatched `serviceName` rather than transforming it.
`presentPairing` only confirms presentation of DeviceDiscoveryUI. On Apple,
`onPeerFound` carries either a subscriber browser endpoint or a publisher's
paired device, both as discovery-scoped opaque handles; neither proves a live
data connection. Android retains subscriber-only radio discovery events.
Stage 7 will use the retained native endpoint or paired device to create the
existing data-path contract. The Android-only example string
`com.wifiaware.stage4` is not a portable library service identity. No
Android↔Apple promise exists.
