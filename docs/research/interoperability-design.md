# Cross-platform interoperability design constraint

Status: baseline architecture requirement. This document does not claim that
Android and Apple devices interoperate; it defines the research and
implementation work required before any such claim.

Android and Apple expose Wi-Fi Aware through different native discovery,
pairing, service-declaration, and network APIs. The library must therefore not
assume that a matching JavaScript method, service string, or security argument
means the two platforms can establish the same connection. Before a shared path
is exposed, research must compare service identity, discovery roles,
pairing/security, data-path establishment, and the application transport on
both ends.

In particular, Apple uses declared services and system-mediated pairing, while
Android exposes runtime discovery and data-path configuration. The initial
research must treat their trust and connection setup as potentially different
until a public Android pairing/data-path mechanism is identified; an
app-provided Android credential must never be assumed to be an Apple pairing
credential.

The roadmap reserves a cross-platform interoperability abstraction stage after
the platform vertical slices. That stage owns: an explicit security/pairing
mode rather than a platform-specific implicit default; a service identity that
both platforms can use; a compatible native transport; and truthful local
capability/error reporting. It may proceed only after the relevant Android and
Apple public APIs are identified. Physical device work then validates the
implemented path; it must not be used to discover an unimplemented abstraction.

Until that stage is implemented and physically tested, platform paths remain
separate vertical slices and the README must not imply Android↔Apple
connectivity. Any later platform API discovery is a refinement of this named
dependency, not a reason to relabel the missing implementation as validation.
