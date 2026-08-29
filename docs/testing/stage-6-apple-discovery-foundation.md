# Stage 6 Apple discovery foundation validation

Date: 2026-08-25, updated 2026-08-28. Status: `IMPLEMENTED — SIMULATOR BUILD AND LAUNCH PASS; DEVICE VALIDATION PENDING`.

Implemented source surfaces:

- `presentPairing(discoverySessionHandle)`, with an explicit Android
  `UNSUPPORTED` implementation required by shared Codegen.
- iOS-26 capability snapshot, opaque session/discovery/peer registry, strict
  host-declared Apple service lookup, DeviceDiscoveryUI pairing/picker
  presentation, paired-device observation, native subscriber browser, and
  module invalidation that cancels tasks, clears native state/event delivery,
  and dismisses presented pairing UI.
- Example-only generated-host configuration: the `Publish`/`Subscribe`
  entitlement values in `example/app.json` and the valid `_rn-aware._tcp`
  `WiFiAwareServices` plist entry reapplied by the Podfile after every
  `react-native-test-app` generation.

Static checks passed on 2026-08-25:

- `yarn lint` exited 0 with eight existing/example `no-void` warnings and no
  errors.
- `yarn typecheck` exited 0.

The generated app was compiled with Xcode for the iOS simulator, and the user
observed it launch. This checks the current generated host and native bridge
only: the simulator reports Wi-Fi Aware unsupported by design.

When that gate is authorized, validate the generated test host before launch:

1. inspect its generated `App.entitlements` for `Publish` and `Subscribe` and
   its `Info.plist` for `_rn-aware._tcp` with both roles;
2. compile against an Xcode version containing the iOS 26 Wi-Fi Aware SDK;
3. sign/install on two eligible devices using the normal Xcode device-deploy
   workflow;
4. on the publisher: attach, publish, open the pairing UI, and confirm a
   paired-peer event; on the subscriber: attach, subscribe, open the device
   picker, pair/select the publisher, and confirm a browser peer event;
5. close both discovery sessions and parent sessions while checking that no
   React Native error screen or late peer event appears.

Stage 7 may consume the documented native peer categories, but neither stage is
complete until its later compile and appropriate device gate passes.
