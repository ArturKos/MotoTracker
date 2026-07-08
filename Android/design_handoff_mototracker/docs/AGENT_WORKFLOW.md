# Autonomous multi-agent implementation workflow (MotoTracker Android)

A supervisor loop that implements the **MotoTracker Android app** (Kotlin + Jetpack
Compose) from its design handoff, using six role-agents, each on the model best
suited to its job. The supervisor runs unattended until the architect reports that
everything in `BACKLOG.md` is implemented (or it hits a hard block), checkpointing
after every step so it survives token-limit pauses and restarts.

The machinery was ported from the SoundShelf desktop workflow and re-targeted to
the Android/Kotlin/Gradle toolchain. **Scope comes from `BACKLOG.md`**, which is
derived from `README.md` (the design handoff) and the `MotoTracker*.dc.html`
prototypes + `screenshots/` (the binding visual/behavioral spec).

## Pipeline

```
        ┌──────────────────────────────────────────────────────────────┐
        ▼                                                              │
   ARCHITECT ─► PROGRAMMER ─► REVIEWER ─► CRITIC ─► TESTER ─► COMMITTER ┘
   (opus)       (sonnet)      (sonnet)    (opus)    (haiku)   (haiku)
        │            ▲   ▲         │          │        │
        │            │   └─ needs_changes ────┘        │
        │            └────── score < 7 / test fail ────┘
        └─ all_implemented ─► STOP (you review)
```

Each role is a separate headless `claude -p` call with its own role system prompt
(`scripts/workflow_roles/<role>.md`) and model. Every role ends its reply with a
machine-readable line `===VERDICT=== {json}` that the supervisor parses to decide
the next stage.

| Role | Model | Responsibility |
|------|-------|----------------|
| architect | Opus 4.8 | Reads `BACKLOG.md` + `README.md`/`.dc.html` + git; picks the ONE next feature and writes its spec + Definition of Done. Stops the loop when nothing is left. |
| programmer | Sonnet 4.6 | Implements exactly the spec: Kotlin/Compose code + KDoc + JUnit unit tests, offline-first, i18n resources. |
| reviewer | Sonnet 4.6 | Checks the diff matches the spec + design, follows Android/Compose conventions, strings are resources, tests exist. |
| critic | Opus 4.8 | Scores the implementation 0–9 against Kotlin/Android best practices; **< 7 sends it back** to programmer or architect. |
| tester | Haiku 4.5 | Runs the **real** Gradle build/tests: `testDebugUnitTest` + `lintDebug` + `assembleDebug`. Instrumented/on-device tests are best-effort. |
| committer | Haiku 4.5 | Commits and `git push` once everything passed; keeps `CLAUDE.md`/`BACKLOG.md` in sync. |

### Definition of Done (enforced every feature)
KDoc on new public API · unit tests in `app/src/test/` · tests build **and pass**
(`./gradlew testDebugUnitTest`) · clean `assembleDebug` + `lintDebug` · CLAUDE.md
conventions (Kotlin, Compose, MVVM, offline-first, string resources in all 6
languages). **Pure on-device/UI behavior** (live GPS, sensors, BLE, maps, Compose
rendering, foreground service, Android Auto) can't be verified headless — those land
as `🔬` (code-complete, awaiting a human on-device/emulator check), not `✅`.

## Prerequisites

- The **Android SDK** installed and discoverable (`ANDROID_HOME`/`ANDROID_SDK_ROOT`
  or `local.properties`). A JDK 17+. The first Gradle run downloads the wrapper
  distribution and SDK components — allow time.
- `claude` CLI on PATH, authenticated.
- Runs inside the **MotoTracker** git repository; the committer pushes there.

## Running

```bash
# Full autonomous loop (resumes from the last checkpoint automatically):
python3 scripts/agent_workflow.py

# Run a single stage and stop (good for watching one step):
python3 scripts/agent_workflow.py --once

# Try it without committing/pushing (skips the committer):
python3 scripts/agent_workflow.py --dry-run --max-iterations 1

# Bound the run:
python3 scripts/agent_workflow.py --max-iterations 5
```

Run it from this project root (where `scripts/` and `BACKLOG.md` live). Each agent
runs with `--permission-mode bypassPermissions` so it can edit, build, and
(committer) push **without prompts** — this is the approved full-autonomy mode.
Keep an eye on it. The very first iteration will scaffold the Gradle project
(BACKLOG item A1); nothing can build before that exists.

Tip: run it in the background and watch progress live:
```bash
nohup python3 scripts/agent_workflow.py > .workflow/run.out 2>&1 &
tail -f .workflow/status        # current agent + what it's doing
tail -f .workflow/progress.log  # full timeline
```

## Watching progress

- `.workflow/status` — one line: which agent is active, model, attempt, current task.
- `.workflow/progress.log` — append-only timeline with timestamps and verdicts.
- `.workflow/transcripts/<iter>-<role>-<attempt>.txt` — full raw output of each call.
- `python3 scripts/agent_workflow.py --status` — print the current state.

## Token / usage-limit handling

If any agent call hits a usage/token/rate limit, the supervisor checkpoints
(`.workflow/state.json`) keeping the **same stage**, sets status `waiting_tokens`,
parses the reset time if reported, sleeps until then (or backs off 5→…→30 min when
unknown), and re-runs the exact stage it paused on. State is on disk, so you can
Ctrl-C and re-launch later — it resumes from the checkpoint.

## Control flow details

- reviewer `needs_changes` → back to programmer with the issue list.
- critic score `< 7` → back to programmer (or architect if the *design* is wrong).
- tester `fail` → back to programmer with the failing log tail.
- After `MAX_ATTEMPTS` (6) loop-backs on one task, it escalates to the architect
  once, then marks the task **blocked** and stops for a human.
- committer success → next iteration (architect picks the next feature).

## Stopping & resetting

- Terminal states: `all_done` (success), `blocked` (needs you), `error`.
- `python3 scripts/agent_workflow.py --reset` clears the checkpoint to start over.

## Tuning

Edit the constants at the top of `scripts/agent_workflow.py`:
`MODEL` (per-role models), `TIMEOUT`, `CRITIC_PASS`, `MAX_ATTEMPTS`, token backoff.
Role behaviour lives in `scripts/workflow_roles/*.md` — edit those to change what
each agent does or how strict it is. **Scope lives in `BACKLOG.md`** — edit that to
add/remove features (keep it finite so the loop can terminate).
