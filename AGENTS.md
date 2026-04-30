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

## MANDATORY HOST-SESSION SAFETY (Constitution §12)

**Forensic incident, 2026-04-27 22:22:14 (MSK):** the developer's
`user@1000.service` was SIGKILLed under an OOM cascade triggered by
`pip3 install --user openai-whisper` running on top of chronic
podman-pod memory pressure. The cascade SIGKILLed gnome-shell, every
ssh session, claude-code, tmux, btop, npm, node, java, pip3 — full
session loss. Evidence: `journalctl --since "2026-04-27 22:00"
--until "2026-04-27 22:23"`.

This invariant applies to **every script, test, helper, and AI agent**
in this submodule. Non-compliance is a release blocker.

### Forbidden — directly OR indirectly

1. **Suspending the host**: `systemctl suspend`, `pm-suspend`,
   `loginctl suspend`, DBus `org.freedesktop.login1.Suspend`,
   GNOME idle-suspend, lid-close handler.
2. **Hibernating / hybrid-sleeping**: any `Hibernate` / `HybridSleep`
   / `SuspendThenHibernate` method.
3. **Logging out the user**: `loginctl terminate-session`,
   `pkill -u <user>`, `systemctl --user --kill`, anything that
   signals `user@<uid>.service`.
4. **Unbounded-memory operations** inside `user@<uid>.service`
   cgroup. Any single command expected to exceed 4 GB RSS MUST be
   wrapped in `bounded_run` (defined in
   `scripts/lib/host_session_safety.sh`, parent repo).
5. **Programmatic rfkill toggles, lid-switch handlers, or
   power-button handlers** — these cascade into idle-actions.
6. **Disabling systemd-logind, GDM, or session managers** "to make
   things faster" — even temporary stops leave the system unable to
   recover the user session.

### Required safeguards

Every script in this submodule that performs heavy work (build,
transcription, model inference, large compression, multi-GB git op)
MUST:

1. Source `scripts/lib/host_session_safety.sh` from the parent repo.
2. Call `host_check_safety` at the top and **abort if it fails**.
3. Wrap any subprocess expected to exceed ~4 GB RSS in
   `bounded_run "<name>" <max-mem> <max-time> -- <cmd...>` so the
   kernel OOM killer is contained to that scope and cannot escalate
   to user.slice.
4. Cap parallelism (`-j`) to fit available RAM (each AOSP job ≈ 5 GB
   peak RSS).

### Container hygiene

Containers (Docker / Podman) we own or rely on MUST:

1. Declare an explicit memory limit (`mem_limit` / `--memory` /
   `MemoryMax`).
2. Set `OOMPolicy=stop` in their systemd unit to avoid retry loops.
3. Use exponential-backoff restart policies, never immediate retry.
4. Be clean-slate destroyed (`podman pod stop && rm`, `podman
   volume prune`) and rebuilt after any host crash or session loss
   so stale lock files don't keep producing failures.

### When in doubt

Don't run heavy work blind. Check `journalctl -k --since "1 hour ago"
| grep -c oom-kill`. If it's non-zero, **fix the offending workload
first**. Do not stack new work on a host already in distress.

**Cross-reference:** parent `docs/guides/ATMOSPHERE_CONSTITUTION.md`
§12 (full forensic, library API, operator directives) +
parent `scripts/lib/host_session_safety.sh`.

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

## MANDATORY ANTI-BLUFF COVENANT — END-USER QUALITY GUARANTEE (User mandate, 2026-04-28)

**Forensic anchor — direct user mandate (verbatim):**

> "We had been in position that all tests do execute with success and all Challenges as well, but in reality the most of the features does not work and can't be used! This MUST NOT be the case and execution of tests and Challenges MUST guarantee the quality, the completion and full usability by end users of the product!"

This is the historical origin of the project's anti-bluff covenant.
Every test, every Challenge, every gate, every mutation pair exists
to make the failure mode (PASS on broken-for-end-user feature)
mechanically impossible.

**Operative rule:** the bar for shipping is **not** "tests pass"
but **"users can use the feature."** Every PASS in this codebase
MUST carry positive evidence captured during execution that the
feature works for the end user. Metadata-only PASS, configuration-
only PASS, "absence-of-error" PASS, and grep-based PASS without
runtime evidence are all critical defects regardless of how green
the summary line looks.

**Tests AND Challenges (HelixQA) are bound equally** — a Challenge
that scores PASS on a non-functional feature is the same class of
defect as a unit test that does. Both must produce positive end-
user evidence; both are subject to the §8.1 five-constraint rule
and §11 captured-evidence requirement.

**Canonical authority:** parent
[`docs/guides/ATMOSPHERE_CONSTITUTION.md`](../../docs/guides/ATMOSPHERE_CONSTITUTION.md)
§8.1 (positive-evidence-only validation) + §11 (bleeding-edge
ultra-perfection quality bar) + §11.3 (the "no bluff" CLAUDE.md /
AGENTS.md mandate) + **§11.4 (this end-user-quality-guarantee
forensic anchor — propagation requirement enforced by pre-build
gate `CM-COVENANT-PROPAGATION`)**.

Non-compliance is a release blocker regardless of context.


## MANDATORY §12 HOST-SESSION SAFETY — INCIDENT #2 ANCHOR (2026-04-28)

**Second forensic incident:** on 2026-04-28 18:36:35 MSK the user's
`user@1000.service` was again SIGKILLed (`status=9/KILL`), this time
WITHOUT a kernel OOM kill (systemd-oomd inactive, `MemoryMax=infinity`)
— a different vector than Incident #1. Cascade killed `claude`,
`tmux`, the in-flight ATMOSphere build, and 20+ npm MCP server
processes. Likely cumulative cgroup pressure + external watchdog.

**Mandatory safeguards effective 2026-04-28** (full text in parent
[`docs/guides/ATMOSPHERE_CONSTITUTION.md`](../../../../docs/guides/ATMOSPHERE_CONSTITUTION.md)
§12 Incident #2):

1. `scripts/build.sh` MUST source `lib/host_session_safety.sh` and
   call `host_check_safety` BEFORE any heavy step.
2. `host_check_safety` has 7 distress detectors including conmon
   cgroup-events warnings (#6) and current-boot session-kill events
   (#7).
3. Containers MUST be clean-slate destroyed + rebuilt after any
   suspected §12 incident. `mem_limit` is per-container, not
   per-user-slice — operator MUST cap Σ `mem_limit` ≤ physical RAM
   − user-session overhead.
4. 20+ npm-spawned MCP server processes are a known memory multiplier;
   stop non-essential MCPs before heavy ATMOSphere work.
5. **Investigation: Docker/Podman as session-loss vector.** Per-container
   cgroups don't prevent cumulative user-slice pressure; conmon
   `Failed to open cgroups file: /sys/fs/cgroup/memory.events`
   warnings preceded the 18:36:35 SIGKILL by 6 min — likely correlated.

This directive applies to every owned ATMOSphere repo and every
HelixQA dependency. Non-compliance is a Constitution §12 violation.



## MANDATORY §12.6 MEMORY-BUDGET CEILING — 60% MAXIMUM (User mandate, 2026-04-30)

**Forensic anchor — direct user mandate (verbatim):**

> "We had to restart this session 3rd time in a row! The system of
> the host stays with no RAM memory for some reason! First make sure
> that whatever we do through our procedures related to this project
> MUST NOT use more than 60% of total system memory! All processes
> MUST be able to function normally!"

**The mandate.** Project procedures MUST NOT use more than **60%
of total system RAM** (`HOST_SAFETY_MAX_MEM_PCT`). The remaining
40% is reserved for the operator's other workloads so the host can
keep serving them while project work proceeds.

**Three consecutive session-loss SIGKILLs on 2026-04-30** during
1.1.5-dev — every one happened while `scripts/build.sh` was running
`m -j5` AOSP. Each Soong/Ninja job peaks at ~5–8 GiB RSS;
collective RSS overran the 60% envelope and the kernel OOM-killer
escalated, taking down `user@1000.service`. **§12.1's pre-flight
check (refusing to start if host already distressed) was not enough**
— the missing piece was an active CONSTRAINT on heavy work itself.

**Mandatory protections (rock-solid):**

1. `HOST_SAFETY_MAX_MEM_PCT` defaults to 60 in
   `scripts/lib/host_session_safety.sh`.
2. `HOST_SAFETY_BUDGET_GB` is computed at source-time from
   `MemTotal × MAX_PCT/100`.
3. `bounded_run` clamps `MemoryMax` down to the budget if the
   caller asks for more (cgroup-level enforcement via
   `systemd-run --user --scope -p MemoryMax=…`).
4. `host_safe_parallel_jobs` and `host_safe_build_jobs` return
   the safe `-j` count given an estimated per-job RSS, capped at
   `nproc`.
5. `scripts/build.sh` wraps `m -j` in `bounded_run`. If the
   build's collective RSS exceeds the budget, only the scope is
   OOM-killed; `user@<uid>.service` stays alive.

**Captured-evidence enforcement.** Pre-build gate
`CM-MEMBUDGET-METATEST` locks all 7 invariants and fires every
pre-build run.

**No escape hatch.** §12.6 has NO operator-facing override flag.
The cap exists for the operator's own protection; bypassing it is
the bluff the §11.4 covenant specifically prohibits. Operators who
need more headroom should reduce parallelism, close other
workloads, or add RAM — NOT raise the percentage.

**Canonical authority:** parent
[`docs/guides/ATMOSPHERE_CONSTITUTION.md`](../../docs/guides/ATMOSPHERE_CONSTITUTION.md)
§12.6.

Non-compliance is a release blocker regardless of context.
