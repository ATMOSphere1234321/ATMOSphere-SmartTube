# AGENTS.md — smarttube-player submodule

Every AI agent working in this submodule MUST comply with the canonical
[ATMOSphere Constitution](../../../../docs/guides/ATMOSPHERE_CONSTITUTION.md)
in the parent repo.

## Non-negotiable summary

1. **Tests for every change**: pre-build `CM-ST*` gate, post-build gate
   (parent Section 13), on-device `test_smarttube.sh`, and a mutation entry
   in `scripts/testing/meta_test_false_positive_proof.sh` proving the gate
   catches regressions. A gate without a paired mutation is a bluff gate.
2. **Both devices flashed and green** before any tag.
3. **Commit + push via `bash scripts/commit_all.sh "…"` from the parent
   repo root.** For submodule source changes: commit inside the submodule
   first, push to every submodule remote, THEN run parent's `commit_all.sh`
   to capture the updated pointer (and any nested-submodule pointer
   updates from `SharedModules` / `MediaServiceCore`).
4. **Tags cascade**: every main-repo tag mirrored on this submodule at its
   current HEAD across every remote it publishes to. Use parent's
   `scripts/testing/release_tag.sh <tag>`.
5. **Changelog discipline**: every tag has `docs/changelogs/<tag>.md` +
   HTML + JSON + TXT exports.
6. **False-success results are literally impossible**: every gate has a
   mutation-test pair; any always-PASS gate is immediately rewritten.
7. **Flock is sacred**: `commit_all.sh` and `push_all.sh` serialised via
   `.git/.commit_all.lock` / `.git/.push_all.lock` — never bypass.

Non-compliance is a blocker regardless of context.

## MANDATORY ANTI-BLUFF VALIDATION (Constitution §8.1 + §11)

**This submodule inherits the parent ATMOSphere project's anti-bluff covenant.
A test that PASSes while the feature it claims to validate is unusable to an
end user is the single most damaging failure mode in this codebase. It has
shipped working-on-paper / broken-on-device builds before, and that MUST NOT
happen again.**

1. Tests MUST validate user-visible behaviour, not just metadata. A gate that
   greps an XML attribute, manifest entry, or build-time symbol is METADATA
   — not evidence. It is allowed ONLY when paired with a runtime / on-device
   test that observes POSITIVE EVIDENCE (real Cue render on secondary,
   real subtitle on TV during real playback, captured logcat showing
   `IVideoOutputManager.routeSubtitleCue` invocation, etc).
2. PASS / FAIL / SKIP mechanically distinguishable. SKIP requires an
   explicit reason. PASS only on observed positive evidence.
3. Every gate MUST have a paired mutation in
   `scripts/testing/meta_test_false_positive_proof.sh` (parent repo).
4. The bar is not "tests pass" but "users can use the feature."
5. No false-success outcome is tolerable. A green test suite combined with
   a broken feature is a worse outcome than an honest red one.

## MANDATORY ABSOLUTE DATA SAFETY — ZERO RISK (Constitution §9)

EVERY destructive repository operation (history rewrite, force-push, branch
deletion, bulk file removal, submodule de-init, object pruning) MUST follow
Constitution §9 without exception. Hardlinked backup, recorded metadata,
post-op gate, no automatic force-push. See parent Constitution §9 for the
canonical 7-step protocol.
