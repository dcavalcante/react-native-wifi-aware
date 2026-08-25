# Stage 3 — Android peer messaging

Status: COMPLETE.

## Goal and boundary

Implement small Android Wi-Fi Aware follow-up messages on top of a live Stage-2 discovery session. The public API adds `sendMessage(discoverySessionHandle, peerHandle, payload)` and `onMessageReceived`. `payload` is a read-only array of byte values (`0..255`); the Codegen spec uses `number[]`. The operation resolves only from Android's message-send success callback.

No pairing, service-specific discovery payloads, retries, ordering/deduplication guarantees, data paths, sockets, file transfer, Apple runtime behavior, or Android-to-Apple claim is in scope.

## Decisions and risks

| Concern | Decision |
|---|---|
| Native API | Use API-26 `DiscoverySession.sendMessage(PeerHandle, messageId, byte[])`, `onMessageSendSucceeded`, `onMessageSendFailed`, and `onMessageReceived`. |
| Payload/event boundary | Validate and copy JS byte values at the bridge. The Stage-2 Codegen `onPeerFound` stream carries serializable events; public `onPeerFound` and `onMessageReceived` are typed JS filters. This is only for small follow-up messages; bulk data remains a future native-stream concern. |
| Peer lifetime | A peer handle is valid only for its discovery handle. Subscriber discovery supplies the publisher peer; a publisher learns a subscriber peer from an inbound message. |
| Promise lifecycle | Native message IDs are unique while pending. Close, termination, availability loss, and module invalidation reject pending sends; late callbacks are ignored. |
| Payload limit | Reject an outbound message exceeding `WifiAwareManager.getCharacteristics().getMaxServiceSpecificInfoLength()` when the characteristic is available; handle a null characteristic defensively. |
| Permissions | Stage 3 adds no manifest or runtime permission beyond Stage 2; retain runtime permission and `SecurityException` handling. |
| Apple | Extend compile-only TurboModule stubs to reject the new method. This is not an Apple runtime claim. |

## Gate record

| Gate | State | Evidence |
|---|---|---|
| Readiness review | PASS | 2026-08-24: focused review returned `READY — PROVISIONAL PHYSICAL VALIDATION PENDING`; no BLOCKING/HIGH finding. Primitive-array Codegen compilation remains the first automated proof. |
| Implementation | PASS | Kotlin message lifecycle, Codegen/public API, Apple rejection stub, focused JVM tests, and visible example harness. |
| Automated validation | PASS | `yarn lint` (0 errors; 4 existing example warnings), `yarn typecheck`, `yarn prepare`, `:react-native-wifi-aware:testDebugUnitTest`, and `:app:assembleDebug` passed. |
| Physical two-device validation | PASS | 2026-08-24: subscriber discovery, tablet-to-phone `ping`, phone-to-tablet `ping`, native acknowledgements, received-payload UI, and explicit close on both devices. See `docs/testing/stage-3-android-peer-messaging.md`. |
| Final review | PASS | 2026-08-25: independent focused review found no BLOCKING/HIGH issue. See `docs/review/stage-3.md`. |

## Execution order

1. Add the Codegen spec and public byte/message types; compile the generated Android boundary before further native work.
2. Implement native peer lookup, pending-message tracking, callback settlement, inbound events, and every existing cleanup path on the serialized handler.
3. Add JVM tests for unique pending IDs, acknowledgement/failure settlement, close-before-ack, availability/module invalidation, and late callbacks.
4. Update the example with vertically accessible discovery, last-peer, send status, received-message log, and teardown actions.
5. Run automated gates. Do not install or touch devices until the harness and automated gates pass.
6. If both devices are available, execute the documented two-device exchange with Metro status, per-device `adb reverse`, actual rendered UI, and explicit teardown. Otherwise record `IMPLEMENTED + CI_VERIFIED — PROVISIONAL: PHYSICAL VALIDATION PENDING`; missing devices alone do not stop independent later work.

Stop only if current Codegen cannot compile the primitive-array contract or Android evidence requires a materially different public API. A missing device is not a stop condition.
