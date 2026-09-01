# Stage 9 cross-platform interoperability validation

Date: 2026-09-01. Status: `IMPLEMENTED — AUTOMATED COMPILE PASS; PHYSICAL
INTEROPERABILITY VALIDATION PENDING`.

Stage 9 introduces an explicit `psk` / `paired` public contract. `psk` remains
Android-only. `paired` configures Android framework-offloaded pairing before
discovery, uses the Apple system-pairing model on iOS, and uses raw TCP on both
platforms. The generated example uses `_rn-aware._tcp` everywhere.

Automated evidence:

- `corepack yarn typecheck` passed on 2026-09-01.
- `example/android/gradlew app:bundleDebug --no-daemon --console=plain
  -PreactNativeArchitectures=arm64-v8a` passed on 2026-09-01 with JDK 17 and
  SDK platform 37.2; it produced `app-debug.aab`.
- `corepack yarn verify:ios` passed on 2026-09-01; its React Native/Xcode log
  ends `Successfully built the app` for the generic iOS Simulator target.

The Android build has a non-failing AGP compatibility warning because the
installed 8.12 plugin is tested through SDK 36 while the library deliberately
compiles against SDK 37.2. The build did compile the public 37.2 paired API.

The simulator cannot establish Wi-Fi Aware. No physical pairing, discovery,
connection, loss, or transport result is claimed here.

## Physical gate

Prerequisites:

1. An iPhone/iPad on iOS 26.4 or later (the subscriber picker needs 26.4),
   signed with the Wi-Fi Aware Publish and Subscribe capability. Its host
   `WiFiAwareServices` declaration must contain `_rn-aware._tcp` for both
   roles.
2. An Android device whose displayed `Paired path` capability is `true`.
   That means Android 17.2 plus Wi-Fi Aware pairing hardware; Android version
   alone is not sufficient.
3. The same current example development build on both devices. Verify Metro's
   status endpoint, configure `adb reverse tcp:8081 tcp:8081` for the Android
   USB device, and visibly confirm the rendered example before beginning.

Run both directions with the single iPhone and Android device:

1. **Android publisher → Apple subscriber.** Start paired publish on Android;
   start paired subscribe on Apple and open **Pair Apple device** to select the
   Android publisher. Start the Android publisher server, then the Apple
   subscriber client after its paired peer appears. Require `connected` on both
   devices.
2. **Apple publisher → Android subscriber.** Start paired publish and the
   Apple pairing UI; start paired subscribe on Android. Start the Android
   subscriber client after it receives its discovery peer, and start the Apple
   publisher server after Apple has a paired peer. Require `connected` on both
   devices.
3. In each direction, close the server path and require exactly one local
   `closed` and remote `lost`/terminal event. Repeat by closing the discovery
   and then the parent session; no late callback or React Native error screen
   is a pass condition.

Record any system pairing UI behavior, discovery state, each data-path event,
and device/OS model. Passing only one direction is not a cross-platform pass.
