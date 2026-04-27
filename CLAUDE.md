# CLAUDE.md — ATMOSphere SmartTube fork

ATMOSphere's platform-signed fork of SmartTube. The `ststable` flavour is the
shipped variant — applicationId `org.smarttube.stable`, launcher label
"ATMOSphere SmartTube". Built from source by parent `scripts/build.sh`
`step_build_smarttube` between the kernel build and the AOSP image build.

## Project Overview

- **Package** (ststable): `org.smarttube.stable`
- **LOCAL_MODULE**: `atmosphere-smarttube` (parent's `prebuilt_apps/Android.mk`)
- **LOCAL_CERTIFICATE**: `platform` — AOSP re-signs at image-assembly time
- **Repo**: `git@github.com:ATMOSphere1234321/ATMOSphere-SmartTube.git`
- **Parent path**: `device/rockchip/atmosphere/smarttube-player`
- **Nested submodules**:
  - `SharedModules/` — common UI/util module
  - `MediaServiceCore/` — YouTube backend
    - `MediaServiceCore/SharedModules/` — its own copy of shared UI

## Build (from parent)

```bash
bash scripts/build.sh --skip-pull --skip-tests --skip-ota
```

`step_build_smarttube` picks Java 17 (gradle 7.5 incompatible with Java 21),
auto-generates `keystore.properties` if absent, runs `./gradlew assemble<flavor>Release`,
selects the universal APK, rejects x86 / armeabi-v7a, and copies it to
`device/rockchip/rk3588/prebuilt_apps/atmosphere-smarttube.apk`.

## ATMOSphere integration points

- `AtmosphereSubtitleForwarder` is injected into the `:common` module and
  hooked from `SubtitleManager.onCues()`. Every ExoPlayer Cue is forwarded to
  the framework's `IVideoOutputManager.routeSubtitleCue` via reflection so
  subtitles render on the secondary display while the primary stays clean
  (Constitution §11 / Fix #115 RR1).
- `VideoPlaybackDetector` (Presenter) lists the ststable applicationId so
  Tier 2 (task-move) routing kicks in when MediaSession reports PLAYING.

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

The canonical authority is `docs/guides/ATMOSPHERE_CONSTITUTION.md` §8.1
("NO BLUFF — positive-evidence-only validation") and §11 ("Bleeding-edge
ultra-perfection") in the parent repo. Every contribution to THIS submodule
is bound by it. Summarised non-negotiables:

1. **Tests MUST validate user-visible behaviour, not just metadata.** A gate
   that greps for a string in a config XML, an XML attribute, a manifest
   entry, or a build-time symbol is METADATA — not evidence the feature
   works for the end user. Such a gate is allowed ONLY when paired with a
   runtime / on-device test that exercises the user-visible path and reads
   POSITIVE EVIDENCE that the behaviour actually occurred (kernel `/proc/*`
   runtime state, captured audio/video, dumpsys output produced *during*
   playback, real input-event delivery, real surface composition, etc).
2. **PASS / FAIL / SKIP must be mechanically distinguishable.** SKIP is for
   environment limitations (no HDMI sink, no USB mic, geo-restricted endpoint
   unreachable) and MUST always carry an explicit reason. PASS is reserved
   for cases where positive evidence was observed.
3. **Every gate MUST have a paired mutation test in
   `scripts/testing/meta_test_false_positive_proof.sh` (parent repo).** A
   gate without a paired mutation is a BLUFF gate.
4. The bar for shipping is not "tests pass" but "users can use the feature."

## MANDATORY DEVELOPMENT PRINCIPLES

1. **Solutions MUST NOT be error-prone** — every fix robust, no new failure modes
2. **Concurrent callers** — UI thread + ExoPlayer + MediaSession threads must coexist
3. **Test the fix on a flashed device**, not just by running unit tests on the host
4. **Subtitle forwarder must never block playback** — Cue forwarding is best-effort

## MANDATORY API KEY & SECRETS CONSTRAINTS

1. **NEVER commit `.env`, signing keystores, or YouTube API keys**
2. `.gitignore` must protect `keystore.properties` and `*.jks`
3. `keystore.properties` is auto-generated per build; never check it in

## MANDATORY COMMIT & PUSH CONSTRAINTS

1. **ONLY use `bash scripts/commit_all.sh "message"` from the PARENT repo root**
2. NEVER use `git commit` / `git push` directly inside this submodule
3. The parent script handles staging, committing, and pushing to ALL remotes —
   including all nested submodule remotes — and captures the updated pointer
   in the parent repo

## MANDATORY SUBMODULE SYNC CONSTRAINTS

1. ALWAYS fetch + pull latest from upstream before pushing our committed changes
2. The fork is rebased against upstream periodically; resolve conflicts carefully
3. NEVER discard upstream changes blindly — every merge must preserve fork features

## MANDATORY TAGGING CONSTRAINTS

1. Tags are NEVER created before BOTH ATMOSphere devices are flashed and validated
2. Tags MUST cascade to this submodule at HEAD (parent's `release_tag.sh`)
3. Tag name: `<major>.<minor>.<patch>-dev[-<sub-version>]`

## Project Context

- Part of ATMOSphere Android 15 firmware for Orange Pi 5 Max (RK3588)
- Replaces pre-26.04.02 community SmartTube APKs (`smarttube.downloader`,
  `com.smarttube.downloader`) — see Fix #118 in parent `CLAUDE.md`
- Subtitle forwarder hooks `:common` module; MediaServiceCore handles YouTube backend
- Pre-build gates: `CM-ST1..CM-ST13` enforce LOCAL_MODULE / LOCAL_CERTIFICATE / forwarder injection / device.mk wiring
