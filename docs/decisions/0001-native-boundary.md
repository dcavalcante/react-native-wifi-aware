# ADR 0001: opaque native handles

**Decision:** keep platform sessions, peers and network objects native; expose stable JS handles and snapshots. **Reason:** Android `PeerHandle` is opaque and Apple Network/Wi-Fi Aware operations are lifecycle-bound. **Consequences:** deterministic close/invalidation is mandatory; future APIs cannot promise identical platform semantics merely for symmetry.
