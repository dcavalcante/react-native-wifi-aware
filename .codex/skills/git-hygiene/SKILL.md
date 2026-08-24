---
name: git-hygiene
description: Maintain clean, intentional Git history for repository changes and milestones.
---

Inspect `git status --short` and the relevant diff before editing or staging. Keep generated build output, local caches, unrelated edits, and failed experiments out of commits. Stage one coherent milestone, validate the staged diff, and use an intentional conventional commit message. Keep one validation record with that milestone; split it only for materially later external evidence. Before a planned push, audit public-facing documentation and validation evidence. Do not leave reachable `fixup!` or temporary corrective commits in unpublished history; when rewrite is authorized, fold them into their intended commits and report the final log and clean status. Do not use destructive Git operations unless explicitly authorized.
