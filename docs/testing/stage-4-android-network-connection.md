# Stage 4 Android network connection validation

Date: 2026-08-25. Status: `COMPLETE — IMPLEMENTED + CI_VERIFIED + PHYSICALLY VERIFIED + FINAL REVIEWED`.

Automated checks passed:

- library Codegen generation for `openDataPath`, `closeDataPath`, and `onDataPathState`
- `yarn lint`
- `yarn typecheck`
- `yarn prepare`
- `example/android/gradlew.bat :react-native-wifi-aware:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`
- focused `DataPathRegistry` lifecycle tests

The example exposes publisher-server request, readiness signal, subscriber-client
request, connection-state log, explicit data-path close, and discovery/session
teardown controls. It disables the server control on subscribers and the client
control on publishers, so the physical procedure cannot select the wrong role.

Required physical procedure: run Metro and verify its status endpoint; configure
`adb reverse tcp:8081 tcp:8081` for each USB device; launch the rendered
example; publish on the server device, subscribe and send hello on the client,
start the server request, observe the readiness message, start the client
request, observe `connected` on both devices, close one data path and observe
remote `lost`, then explicitly close both discoveries and parent sessions.

Physical result (Metro development build):

- Metro's status endpoint responded before launch; `adb reverse tcp:8081
  tcp:8081` was configured for both USB devices.
- The rendered harness was inspected by its accessibility hierarchy after every
  action; neither device showed a React Native error screen.
- On SM-M515F (API 31), publish, receive the subscriber's `hello`, start the
  server request, and send readiness. On SM-X710 (API 36), subscribe, discover
  the publisher, send `hello`, receive readiness, and immediately start the
  client request.
- The server reported `connected: Server socket accepted client`; the client
  reported `connected: Client socket connected`.
- Closing the server path reported local `closed` and remote client `lost`.
  Both devices then reported `Discovery and parent session closed.`
- After the final lifecycle review fixes, the fresh APK was reinstalled and the
  same complete flow passed again. The focused JVM suite passed 4/4, including
  availability cleanup retirement; the socket executor is one worker with a
  one-task bounded queue and one active data path per discovery session.

An earlier exploratory attempt failed because the subscriber request began only
after the publisher's 30-second request deadline. Wi-Fi Aware service diagnostics
showed both secure in-band requests were accepted; the clean, readiness-gated
attempt above was then performed within that deadline.

This record makes no claim about Apple runtime behavior, file transfer, or
Android-to-Apple interoperability.
