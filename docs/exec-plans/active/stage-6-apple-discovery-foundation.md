# Stage 6 — Apple discovery foundation

Status: ACTIVE. Expected completion state: `PROVISIONAL — PHYSICAL VALIDATION
PENDING`.

## Scope and public contract

Implement the Apple side of the existing opaque session/discovery/peer contract
on iOS 26+. Add one explicit API:

```ts
presentPairing(discoverySessionHandle: DiscoverySessionHandle): Promise<void>
```

It presents the Apple system pairing UI for the live discovery role and resolves
once presentation is accepted; it does not claim a peer was paired. `attach`,
`publish`, `subscribe`, `closeDiscoverySession`, `closeSession`, and
`onPeerFound` gain Apple implementations. A discovered Apple endpoint stays
native and is represented to JS by an opaque, discovery-scoped `PeerHandle`.

`publish` and `subscribe` accept only the exact fully-qualified service name
declared for their role in the host application's `WiFiAwareServices`
`Info.plist` dictionary. They reject undeclared, role-mismatched, invalid, or
closed inputs; they never rewrite an Android-style service name. The library
does not and cannot insert an entitlement or `Info.plist` entry into a
consumer's app target.

`onPeerFound` means *eligible peer*, not only radio discovery. Android retains
its existing subscriber-discovery behavior. On Apple, a subscriber emits it
after its browser discovers an endpoint; a publisher emits it after
`presentPairing` observes a newly accessible paired device. The latter is the
publisher's opaque `WAPairedDevice` handle required for its existing
server-role `openDataPath` call in Stage 7. No connection is implied by either
event.

## Boundaries, successor, and non-goals

Use a Swift adapter behind the existing Objective-C++ TurboModule. It owns
Apple `WAPublishableService`, `WASubscribableService`, paired-device snapshots,
system pairing controllers, and Network browser tasks; JavaScript sees
only strings and serializable events. A serial queue/actor-like single owner
invalidates children before parents and drops late task callbacks.

Stage 7, in a separate branch/PR, consumes the retained Apple endpoint and
publisher service/paired-device ownership to construct the actual
`NetworkListener` and implement the existing `openDataPath`/`closeDataPath` and
`onDataPathState` contract. A publisher's server call uses the paired-device
peer recorded above; its listener accepts the corresponding incoming connection
onto that returned data-path handle. A subscriber's client call uses the
browser-endpoint peer. It adds no Apple pairing or service-name semantics.

This stage does not add JS byte streams, files, a registry owned by the
library, Android↔Apple compatibility, reconnection, background execution
guarantees, accessory onboarding, or a physical Apple runtime claim.

## Research decisions

| Concern | Decision |
|---|---|
| Availability | **Documented:** guard iOS 26 and check `WACapabilities.supportedFeatures` for `.wifiAware`. Apple provides no Android-equivalent mutable availability snapshot, so `isAvailable` is the supported-feature result; operation errors remain authoritative. |
| Services and signing | **Documented:** look up only `WAPublishableService.allServices` / `WASubscribableService.allServices`, which are populated from the host target's signed plist. Require the matching `Publish` / `Subscribe` entitlement role in the host app. |
| Pairing | **Documented:** use `DeviceDiscoveryUI` for app-to-app pairing. The publisher presents a device-pairing controller and the subscriber presents a device picker; neither is treated as connection success. The paired-device observer creates an eligible publisher peer, making the existing server role usable without an implicit inbound-path API. |
| Discovery | **Documented:** retain a Wi-Fi Aware `NetworkListener` for a publishable service and a `NetworkBrowser` for a subscribable service, restricted to paired/user-selected devices. Store endpoints and paired-device identities only natively and emit discovery-scoped eligible peer handles. |
| Lifecycle | **Inference:** one Swift-owned registry gives sessions/discoveries/generations deterministic idempotent close. Presentation, browser/listener state, and asynchronous paired-device updates are ignored after retirement. |
| Stage 7 handoff | **Documented + inference:** retain discovered endpoints and publisher listener ownership so Stage 7 can create a `NetworkConnection` without exposing network objects to JS. |
| Validation debt | **Physical verification required:** the entitlement, plist configuration, pairing UI, actual discovery, and task cancellation require a paid Apple team and two eligible iOS/iPadOS 26 devices. User-directed deferral also leaves the iOS compile gate pending. |

Sources: [Adopting Wi-Fi Aware](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware) · [DeviceDiscoveryUI](https://developer.apple.com/documentation/DeviceDiscoveryUI) · [Apple peer-to-peer sample](https://developer.apple.com/documentation/WiFiAware/Building-peer-to-peer-apps) · [paired devices](https://developer.apple.com/documentation/wifiaware/wapaireddevice/alldevices).

## Implementation sequence

1. Extend Codegen/public wrappers with `presentPairing`; add an Android
   `UNSUPPORTED` stub required by the generated interface. Preserve every
   existing Android behavior and event name.
2. Add a Swift iOS-26 adapter and Objective-C++ bridge. Implement capability
   snapshots, strict declared-service lookup, opaque registries, and idempotent
   session/discovery close/invalidation.
3. Implement publisher/subscriber pairing presentation and paired-device
   observation, then listener/browser start and native peer-handle emission.
4. Update the example with a visible pairing action and platform-specific
   status guidance. Configure the generated `react-native-test-app` host
   reproducibly: `example/app.json` declares the `Publish` and `Subscribe`
   entitlement values, and `example/ios/Podfile` reapplies the valid static
   `_rn-aware._tcp` `WiFiAwareServices` plist entry after each generated-project
   run. This is example-only; consumer setup is documented separately.
5. Record static checks. Per the approved validation economy, defer iOS build,
   signing, install, and physical testing to the later Apple validation stage.

## Gate record

| Gate | State | Evidence |
|---|---|---|
| Readiness review | PASS | 2026-08-25: independent review returned `READY — PROVISIONAL PHYSICAL VALIDATION PENDING`; no BLOCKING/HIGH findings. It required and then confirmed the publisher paired-peer mapping, Android generated stub, reproducible test-app configuration, and explicit deferred compile/physical debt. |
| Implementation | PASS | Shared pairing API and Android stub; Swift registry/capabilities/strict service lookup/system pairing/subscriber browser; Objective-C++ event bridge; generated-example entitlement/plist configuration; and visible pairing control are present. |
| Static validation | PENDING | `yarn lint` exited 0 (8 existing/example warnings; 0 errors) and `yarn typecheck` exited 0 on 2026-08-25. User also deferred Codegen/package generation, so this gate remains pending rather than being called CI-verified. |
| Apple compile/signing/device validation | PENDING | Deferred to the dedicated later Apple validation stage; this stage must not be presented as runtime-ready. |
| Final source review | PASS | 2026-08-25: independent review returned `READY — PROVISIONAL PHYSICAL VALIDATION PENDING`. It required the completed module-invalidation path: retire/cancel all Apple work, clear event sink/state, dismiss pairing UI, and avoid retaining a stale bridge. No BLOCKING/HIGH findings remain. |

## Stop conditions

Stop only if Apple's current SDK cannot express the proposed pairing/controller
bridge without changing the public contract, or if Stage 7 cannot consume a
discovered endpoint under the existing data-path API. A later compile or
physical failure is a real gate failure to diagnose, not evidence that this
provisional stage passed.
