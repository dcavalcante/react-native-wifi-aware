# Ecosystem and interoperability

A bounded npm/GitHub search found no maintained dedicated React Native Wi-Fi Aware/NAN implementation. `react-native-wifi-p2p` is Wi-Fi Direct, and `react-native-wifi-reborn` manages infrastructure Wi-Fi; neither solves this API. No authoritative evidence was found for Android↔Apple Wi-Fi Aware interoperability. This is a search result, not proof of absence; interoperability remains a physical-test question.

The lack of a maintained React Native precedent is an additional reason to keep
cross-platform feasibility explicit from the outset. See
[`interoperability-design.md`](interoperability-design.md) for the required
compatibility boundary and planned abstraction stage.

Apple documents Wi-Fi Aware as cross-platform, and Android API version 37.2
documents framework-managed pairing before a NAN data path. This establishes
that an Apple↔eligible-Android paired path is an implementable library target;
it does not establish runtime success. The dedicated feasibility and Stage 9
implementation blueprint are in
[`cross-platform-interoperability.md`](cross-platform-interoperability.md).
