# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this directory is

The **MotoTracker Android app project root** — a native **Kotlin + Jetpack Compose** motorcycle ride logger (functional analogue of Yamaha MyRide). Two things live here side by side:

1. **The design spec (immutable):** `README.md` (the design handoff), `MotoTracker.dc.html` + `MotoTracker Android Auto.dc.html` (HTML prototypes), `screenshots/`, and `icon.svg`. These define the binding look/behavior — **never edit or overwrite them.**
2. **The app being built from that spec:** the Gradle/Compose project (created by the workflow on its first run) plus the autonomous implementation workflow under `scripts/`.

The rest of the MotoTracker repo (firmware, PHP `backend/`, `integrations/`) is unrelated to this directory; see the workspace-root `CLAUDE.md` for those. This app syncs to the existing GPStrack server at `http://192.168.1.145/gpstrack`; a hard requirement is that it **works fully offline** (no login, no server → save locally, queue for later sync).

## Autonomous implementation workflow

The app is implemented by a supervised **multi-agent loop** (ported from the SoundShelf desktop project, re-targeted to Android). Do not hand-implement features ad hoc — the loop owns that.

- Driver: `python3 scripts/agent_workflow.py` (`--once` / `--dry-run` / `--status` / `--reset`). Pipeline: architect → programmer → reviewer → critic → tester → committer, each a headless `claude -p` call with a role prompt in `scripts/workflow_roles/*.md`.
- **Scope is `BACKLOG.md`** — the finite, authoritative task list the architect works from (derived from `README.md`). Edit `BACKLOG.md` to change what gets built; keep it finite so the loop terminates.
- Full operator guide: `docs/AGENT_WORKFLOW.md`. Runtime state/logs live in `.workflow/` (gitignored).
- **Definition of Done** per feature: KDoc on public API · JUnit unit tests in `app/src/test/` that pass (`./gradlew testDebugUnitTest`) · clean `assembleDebug` + `lintDebug` · every user-facing string a resource in all 6 languages (pl/en/de/fr/cs/ru). Pure on-device/UI behavior (live GPS, sensors, BLE, maps, Compose rendering, foreground service, Android Auto) can't be verified headless → tracked as `🔬` (awaiting human on-device check), not `✅`.
- Layered architecture: UI/Compose → ViewModel → Domain/UseCase → Data/Repository → Room/DataStore + Network; dependencies point downward only.

## Source of truth

`README.md` (in Polish) is canonical and binding: design tokens (colors, type scale, spacing), every screen/flow, the three themes (cockpit/grid/light), state shape, and the offline behavior. When reproducing UI, treat README hex/size/weight values as final. `screenshots/` shows all rendered views. Don't infer the spec from the prototype markup when the README states it explicitly — the README wins.

## The `.dc.html` prototype format — read, do not port

The two `.dc.html` files are prototypes built in an in-house React-based "Design Component" runtime. They are a **visual + behavioral specification**, not code to copy into production.

- `MotoTracker.dc.html` — full phone app, all screens.
- `MotoTracker Android Auto.dc.html` — car head-unit view.
- `support.js` — the runtime. It is **generated** (`// GENERATED from dc-runtime/src/*.ts`) and exists only to preview `.dc.html` in a browser. Never edit it and never move it into the real app.

Structure of a `.dc.html` file:
- A `<x-dc>` block holds an HTML template. `{{ expr }}` interpolates; `<sc-if value="{{ … }}">` and `<sc-for>` are the control-flow directives; `onClick="{{ handler }}"` binds events.
- A `<script type="text/x-dc" data-dc-script data-props="…">` block defines `class Component extends DCLogic`. `data-props` (HTML-escaped JSON) declares editor-tunable props (`theme`, `accent`, `units`) with defaults.
- The Component holds `state = { … }` (the full app state model — mirror it in a ViewModel/store), lifecycle methods (`componentDidMount` runs the 1 Hz recording simulation), action methods (`startRec`/`stopRecording`/`showToast`/…), and `renderVals()`, which returns the computed values (including the resolved `THEMES` map) the template reads.
- i18n lives in the static `TRANS` and `T2` dictionaries (6 languages: pl/en/de/fr/cs/ru), merged by `L()`. These dictionaries are the authoritative source for exact UI strings — pull copy from here, not by eye.

Simulated-only in the prototype (must become real in the app): GPS (foreground service + FusedLocationProvider), lean angle (accel/gyro), weather API, GPX import/export, GPStrack HTTP client + local DB (Room) + retry queue, BLE advertise/scan for "waves", and GPS→road map-matching (the "Korekcja GPS / na drodze" feature).

## Previewing the prototypes

The `.dc.html` prototypes have no build step of their own — open one directly in a browser with `support.js` sitting next to it (it loads React from a CDN, so preview needs internet). This is only for viewing the design reference; the actual app builds with Gradle (see the workflow section above).

## Assets

`icon.svg` is the app launcher icon/logo — generate adaptive-icon variants from it. Fonts (Barlow, Barlow Semi Condensed, JetBrains Mono) come from Google Fonts; bundle them as app resources rather than fetching at runtime.
