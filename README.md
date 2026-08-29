# react-native-wifi-aware

An open-source React Native library for a modern, capability-aware Wi-Fi Aware (NAN) abstraction.

The project uses a TypeScript TurboModule API with Kotlin on Android and an Objective-C++ bridge for a future Apple implementation. It supports bare React Native; Expo development builds can consume native code, while Expo Go cannot.

## Status

The repository is intentionally early-stage. Android capability, discovery,
follow-up messaging, and secure data paths are physically verified on two
Android devices. Apple iOS-26 discovery, pairing, and network-connection code
is provisional: its source and static checks are present, and simulator
compilation passed; entitlement signing and two-device runtime validation
remain pending. File transfer and Android-to-Apple interoperability are not
implemented. Android's current app-managed PSK data path is Android-only;
Stage 9 adds the Android 17 framework-paired path required for an
Apple-compatible connection. See the [roadmap](docs/ROADMAP.md),
[architecture](docs/ARCHITECTURE.md), and [research notes](docs/README.md)
before relying on it.

## Installation


```sh
npm install react-native-wifi-aware
```


Native Android permissions supplied by the library merge into the consuming app. Platform availability remains a runtime capability question.

## Initial API

The current API provides a synchronous Android capability snapshot plus Android API-26+ attachment and basic discovery. Discovery needs the platform's runtime nearby-Wi-Fi permission; this library declares the relevant manifest permissions but deliberately does not prompt for them.

```ts
import { getCapabilities } from 'react-native-wifi-aware';

const { isSupported, isAvailable } = getCapabilities();
```

```ts
import { attach, closeSession, subscribe, onPeerFound } from 'react-native-wifi-aware';

const session = await attach();
await subscribe(session, { serviceName: 'com.example.demo' });
const subscription = onPeerFound(({ discoverySessionHandle, peerHandle }) => {
  // Opaque native handles; peerHandle is scoped to discoverySessionHandle.
});

subscription.remove();
await closeSession(session);
```

On Android API 24-25 and devices without Wi-Fi Aware, both capability fields are `false`. On API 26 and above, `isSupported` reports the hardware feature and `isAvailable` reports current service availability. On Apple iOS 26+, both fields reflect Apple's supported-feature snapshot; Apple has no Android-equivalent mutable availability query, so individual operations remain authoritative. The Apple implementation has passed its simulator compiler gate; signing and physical-device gates remain pending.

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
