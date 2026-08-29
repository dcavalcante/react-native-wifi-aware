# Stage 7 Apple network connection validation

Date: 2026-08-25, updated 2026-08-28. Status: `IMPLEMENTED — SIMULATOR BUILD
AND LAUNCH PASS; DEVICE VALIDATION PENDING`.

Implemented source surfaces:

- iOS forwarding for the pre-existing `openDataPath`, `closeDataPath`, and
  `onDataPathState` contract;
- a discovery-owned, one-live-path registry that terminal-guards callbacks,
  cancels listener/connection tasks before parent teardown, and sends only
  serializable state events to JavaScript;
- a publisher `NetworkListener` restricted to its selected paired device and
  a subscriber `NetworkConnection` from its retained `WAEndpoint`, both using
  the default bulk Wi-Fi Aware parameters and `TLS()`;
- an iOS example flow with no Stage-3 message call: pair devices, start the
  publisher listener, then start the subscriber client and inspect the visible
  data-path state log.

The Android-required nonempty passphrase remains accepted by the shared API;
on Apple it is intentionally not a Wi-Fi credential or TLS-PSK. Apple system
pairing protects the Wi-Fi layer. This stage exposes no byte stream, framing,
file transfer, or derived `WASharedSecret`.

The subscriber's system picker requires iOS 26.4 or later: the SDK makes its
`NWEndpoint` to `WAEndpoint` conversion available at that version. On iOS
26.0–26.3 the picker path rejects with `UNSUPPORTED`.

The current generated example passed the Xcode simulator compile, and the user
observed the app launch. Static checks also passed: `corepack yarn lint` and
`corepack yarn typecheck` exited 0. The simulator cannot validate Wi-Fi Aware.
No device runtime, signing, data-transfer, or cross-platform claim follows from
this record.

An independent final source review returned `READY — PROVISIONAL PHYSICAL
VALIDATION PENDING` after verifying the Codegen bridge, event routing,
publisher/subscriber peer mapping, lifecycle cleanup, and the iOS harness flow.

When the dedicated Apple gate is authorized:

1. first perform the Stage 6 generated-host entitlement/plist inspection and
   normal Xcode device deployment on two eligible iOS/iPadOS devices;
2. on the publisher, attach, publish `_rn-aware._tcp`, open **Pair Apple
   device**, complete system pairing, wait for the visible paired-peer state,
   and press **Start publisher server data path**;
3. on the subscriber, attach, subscribe to the same service, open **Pair
   Apple device**, select/complete pairing with the publisher, wait for its
   peer state, then press **Start subscriber client data path**;
4. require `connected` on both devices without a React Native error screen;
   close either path and confirm one visible terminal state with no late
   callback. Repeat with parent discovery/session close;
5. record entitlement/service errors, listener failure, client failure, loss,
   and the exact TLS/network state details before changing the public contract.

This is connection-lifecycle validation only. It cannot validate file transfer,
which remains a later native transport design.
