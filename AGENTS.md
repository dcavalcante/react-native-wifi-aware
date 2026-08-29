# Agent guide

`react-native-wifi-aware` is a New Architecture React Native library. Keep the generated TurboModule/Codegen scaffold authoritative; native platform objects never cross the JS boundary. Apple runtime testing is deferred—not a global blocker—when no eligible device is available.

## Default workflow

The root agent owns scope, technical decisions, implementation integration, and commits. Start with the roadmap, relevant current code, and existing research; do not repeat settled research unless a source changed or an unresolved question affects the task.

For a routine, mechanical, or documentation-only change, work directly and run proportionate checks. For an approved native stage with lifecycle, permission, public-API, or cross-platform consequences, write a short active plan, delegate only the unresolved specialty, implement the smallest scoped change, validate it, and update the roadmap plus one validation record when milestone evidence changes. Split validation records only when material external evidence arrives later.

Use one independent reviewer only for a public API, lifecycle, permission, Codegen, or cross-platform semantic decision. Review the focused diff and validation evidence; do not commission duplicate general reviews. Use an execution plan only when it materially reduces multi-step uncertainty. Do not promise Android↔Apple interoperability without physical evidence.

## Cross-platform feasibility and handoff

Before implementing the first native path that is intended to coexist with, or
eventually interoperate with, another platform, record a compact compatibility
matrix covering discovery/service identity, pairing/security, data-path setup,
and application transport. Classify each pair as implemented, implementable
from a named public API, unsupported, or physically unvalidated. Distinguish
an SDK compile dependency from a runtime OS/framework/hardware dependency.

If that matrix reveals a missing native feature, public-contract change, or
transport mismatch, map it to a named implementation stage immediately. Do not
label that work as cross-platform *validation* only, and do not leave its
feasibility implicit in a later device test. The README status must state the
material limitation as soon as research establishes it.

Every native-stage handoff must name: the exact platform combinations covered,
the known incompatible combinations, the API/security/transport reason, the
next implementation work, and the physical evidence still missing. A focused
readiness reviewer must verify that this handoff agrees with current code and
that a direct successor owns every identified gap.

## Milestone readiness

Before starting a native implementation stage, and after a material roadmap or architecture revision, create one concise active execution plan and obtain a focused readiness review. The plan maps each relevant researched concern to an implementation stage, explicit non-goal, deferred validation gate, or unresolved risk; it also records scope, non-goals, dependencies, validation state, physical-validation debt, and stop conditions.

The reviewer checks that the next stage and its direct successors have coherent API/lifecycle boundaries, are neither catch-all nor trivial prerequisite milestones, and match the current code, decisions, research, and validation evidence. Mark the result `READY`, `READY — PROVISIONAL PHYSICAL VALIDATION PENDING`, or `BLOCKED` with the exact unresolved design dependency. Either `READY` state authorizes implementation; only `BLOCKED` stops it. This check is not required for routine, mechanical, or documentation-only work.

## Git workflow

Use a short-lived branch for each native roadmap stage; keep its commits logical and merge only an intentional, reviewable stage history to `main`. Documentation-only or small deterministic fixes may commit directly to `main`. Do not merge failed experiments, reachable `fixup!` commits, or provisional work presented as complete. Use a pull request when it provides a review checkpoint, and require passing CI before protecting or merging to `main`.

## Physical React Native validation

Before calling a development-build device test successful, verify Metro's status endpoint, configure `adb reverse tcp:8081 tcp:8081` for each USB device, launch the resolved activity, and confirm the expected rendered UI or native result. An installed APK or resumed activity alone is not a passing React Native validation. Asset-bundle builds do not use Metro; record the run mode and evidence.

Before a stage with a physical interaction gate is declared ready for that gate, inspect the shipped example or test harness as a user would: every required action, observable success/failure signal, permission path, and teardown action must be accessible without off-screen or developer-only controls. Build the harness before requesting hardware; record its exact multi-device procedure in the validation plan.

## Terminal execution rule

For an approved implementation stage, continue until a definite PASS, a definite FAIL with its resolved cause, or a genuine external dependency. Lost command output, a missing terminal session, partial build output, background-agent timeout, or an intermediate validation failure is an unknown state to investigate and resolve—not a stopping point or progress report. Do not return control for routine build, test, install, lifecycle, or integration work.

## Validation economy

Use the smallest check that can answer the current question. Do not rebuild, reinstall, clean, restart Metro, or alter device setup on inference alone. A user's direct observation of the current screen is valid physical evidence; record it instead of reproducing it. Escalate from static checks to build/install/device work only when that level is necessary for the changed surface or to resolve a concrete contradiction. After one failed corrective loop, stop and re-diagnose rather than repeating the same class of command.

## Stage gate record

Keep one short gate record in the active plan. A gate is `PASS`, `FAIL`, `PENDING`, or `UNKNOWN`; every `PASS` names its command or physical observation. Do work in this order: implementation, automated validation, then physical validation and review. An exploratory device smoke test may happen earlier to diagnose an integration issue, but it must be labelled exploratory and cannot satisfy the physical gate. Never infer a failed build from lost output, or infer a successful runtime test from installation/activity launch alone.

For an iOS-native change, run `yarn verify:ios` before recording an Apple
simulator-build pass. GitHub Actions uses that same command. Its zero exit is
required evidence; a previously installed app, a stale build product, partial
compiler diagnostics, or a simulator launch does not prove the current source
compiled.

## Specialist roles

Invoke specialists on demand: Android or Apple API uncertainty, React Native/Expo/Codegen ambiguity, a substantive approved implementation stage, or an independent consequential review. Ecosystem searches and routine repository stewardship are root-agent responsibilities; reserve a separate history audit for a planned push.

## Release readiness

Before calling a branch or milestone ready to push, inspect public-facing documentation against the current code, roadmap, and validation evidence; remove generator placeholders and stale claims. In unpublished history, fold reachable `fixup!` or corrective commits into their intended milestones when authorized, then report the final log and clean status without waiting for the user to discover them.

Immediately before every public push, run `yarn lint` and `yarn typecheck` against the final tree and record their exit results. A formatter run or an earlier check is not evidence for a later amend. When a changed surface has a local build check, run the smallest corresponding build as well.

## Effort and escalation

Use the active harness's least costly capable default for routine work and increase effort only for architecture, difficult ambiguity, subtle correctness review, or consequential cross-platform decisions—not documentation, Git housekeeping, or routine configuration. Do not pin provider or model names in project workflow files. Escalate for reasoning difficulty or consequence of error—not task size alone.
