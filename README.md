# react-native-wifi-aware

An open-source React Native library for a modern, capability-aware Wi-Fi Aware (NAN) abstraction.

The project uses a TypeScript TurboModule API with Kotlin on Android and an Objective-C++ bridge for a future Apple implementation. It supports bare React Native; Expo development builds can consume native code, while Expo Go cannot.

## Status

The repository is intentionally early-stage. Android capability, discovery,
follow-up messaging, and the PSK data path are physically verified on two
Android devices. Apple iOS-26 discovery, pairing, and network-connection code
remains provisional pending entitlement-signed device tests. Stage 9 implements
and compile-verifies the Android 17.2 framework-paired path, explicit
`psk`/`paired` contract, shared `_rn-aware._tcp` example service, and raw-TCP
common primitive. iPhone↔Android runtime interoperability is still physically
unverified. File transfer remains out of scope. See the [roadmap](docs/ROADMAP.md),
[architecture](docs/ARCHITECTURE.md), and [research notes](docs/README.md)
before relying on it.

## Installation


```sh
npm install react-native-wifi-aware
```


Native Android permissions supplied by the library merge into the consuming app. Platform availability remains a runtime capability question.

## API

The current API provides a synchronous local capability snapshot, attachment,
discovery, platform pairing UI where applicable, and native data-path lifecycle
handles. Discovery needs the platform's runtime nearby-Wi-Fi permission; this
library declares the relevant Android manifest permissions but deliberately does
not prompt for them.

```ts
import { getCapabilities } from 'react-native-wifi-aware';

const { isSupported, isAvailable } = getCapabilities();
```

```ts
import {
  attach,
  closeDiscoverySession,
  closeSession,
  onPeerFound,
  subscribe,
} from 'react-native-wifi-aware';

const session = await attach();
const discovery = await subscribe(session, {
  serviceName: '_my-service._tcp',
  securityMode: 'paired',
});
const subscription = onPeerFound(({ discoverySessionHandle, peerHandle }) => {
  // Opaque native handles; peerHandle is scoped to discoverySessionHandle.
});

subscription.remove();
await closeDiscoverySession(discovery);
await closeSession(session);
```

`getCapabilities()` also exposes `isPairedDataPathSupported`. On Android it is
true only on a Wi-Fi Aware device running the Android 17.2 paired-path API; on
Apple it reflects Wi-Fi Aware feature support. It only describes the local
primitive, never a remote peer or completed pairing. Android API 24-25 and
devices without Wi-Fi Aware report all relevant capabilities as `false`; Apple
has no Android-equivalent mutable availability query, so individual operations
remain authoritative.

`openDataPath` has explicit, non-interchangeable security modes:

```ts
await openDataPath(discovery, peerHandle, {
  role: 'client',
  security: { mode: 'paired' },
});

await openDataPath(discovery, peerHandle, {
  role: 'client',
  security: { mode: 'psk', passphrase: 'android-only-secret' },
});
```

`paired` is the only Apple mode and is the only mode intended for future
iPhone↔eligible-Android use. `psk` is an Android↔Android path; it cannot be
made Apple-compatible by using the same service name or passphrase. The API
only establishes native connection lifecycle; it does not expose a JavaScript
byte stream, framing, or file transfer.

On Apple iOS 26, the host application must add the Wi-Fi Aware capability with
the `Publish` and/or `Subscribe` entitlement values and declare every service
in its own `Info.plist` under `WiFiAwareServices`. Apple service names are
static, fully-qualified `_name._tcp` or `_name._udp` values; pass that exact
declared name to `publish` or `subscribe`. Apple rejects undeclared or
role-mismatched names and never converts Android names. Use
`presentPairing(discoveryHandle)` to present the system pairing UI; resolving
only means that UI was shown, not that pairing or a connection succeeded.

## Development

```sh
yarn
yarn typecheck
yarn lint
yarn prepare
```

The example app lives in `example/`. For Android device testing, start Metro with `yarn example start`, ensure `adb reverse tcp:8081 tcp:8081` for a USB-connected device, then run `yarn example android`.


## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
