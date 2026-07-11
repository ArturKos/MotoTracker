# BACKLOG — finite implementation scope (source of truth for the architect agent)

This file is the **authoritative, finite checklist** the autonomous workflow's
architect agent works from. It is derived from the design handoff in `README.md`
and the `MotoTracker*.dc.html` prototypes. The architect must:

1. Read this file every iteration.
2. **Verify each `⬜` item against the actual code** (it may already be done).
3. Tick it `✅` when fully satisfied (feature + KDoc + unit tests that build & pass
   `./gradlew testDebugUnitTest` + clean `assembleDebug` + clean `lintDebug`),
   updating this file.
   - **EXCEPTION — pure on-device / UI behavior** (Compose rendering on screen,
     live GPS, accelerometer lean angle, map tiles, BLE "waves", foreground-service
     recording, Android Auto head-unit, real weather/HTTP round-trips): the loop
     CANNOT verify these (the tester is headless — no device/emulator/GPS). Once the
     code + unit-tested seams (ViewModel logic, mappers, parsers, repositories with
     injected fakes) are done, mark them **`🔬`** ("code-complete, awaiting human
     on-device confirmation"), NOT `✅`. Only claim the seam is wired and unit-tested.
4. Pick the **next `⬜`** item as the iteration's task. `🔬` items are NOT re-picked.
5. When **every item in A, B and D is `✅` or `🔬`**, emit `all_implemented` and STOP.

Status legend: `⬜` todo · `✅` done & verified · `🔬` code-complete, needs human on-device check.

**Do NOT invent new scope.** Speculative refactors, extra "nice to have" tests, or
cosmetic polish are NOT tasks. If only "Out of scope" (section C) remains, the
project is complete → `all_implemented`.

**Ordering rule:** items in section A are foundational — do them top-to-bottom.
Nothing in B can be built or tested before **A1 (project scaffold)** is `✅`.

## Build environment (this machine — respect it)

Android Studio + SDK are already installed. To keep the first build offline-safe
(the SDK has **no `cmdline-tools`/sdkmanager**, so Gradle cannot auto-download or
license new SDK packages), the scaffold MUST target what is installed:
- **`compileSdk = 36`** and **build-tools `36.0.0`** — the ONLY installed platform is
  `android-36` (build-tools 35.0.0 & 36.0.0). Do NOT pick compileSdk 34/35 (not
  installed → download would fail). `minSdk = 26`, `targetSdk = 36`.
- Use an **AGP version that supports API 36** (AGP 8.9+; matches Studio AI-251) via
  the version catalog, with a compatible Kotlin 2.x + Compose compiler plugin.
- The SDK path is provided in `local.properties` (`sdk.dir`, gitignored) — do not
  hardcode it elsewhere.
- The machine JDK is **21** (system + Studio JBR). Set Java **source/target
  compatibility to 17** (`compileOptions`/`kotlin jvmTarget "17"`); do NOT pin a
  `jvmToolchain(17)` that would force downloading a JDK 17. Building on JDK 21 is fine.

---

## A. Foundation & infrastructure (do in order)

| #  | Item | Status |
|----|------|--------|
| A1 | **Android Studio / Gradle project scaffold**: `settings.gradle.kts`, root + `app/` module `build.gradle.kts`, `gradle/libs.versions.toml` version catalog, Compose + Kotlin, `AndroidManifest.xml`, `MainActivity`, min/target SDK, `.gitignore`, a trivial passing unit test, `./gradlew assembleDebug` + `testDebugUnitTest` green. | ✅ |
| A2 | **Design system / theme**: Compose `Theme`/`Color`/`Type`/`Shape` encoding the README tokens; the three themes (cockpit/grid/light) + user accent color; Barlow / Barlow Semi Condensed / JetBrains Mono bundled as font resources. | ✅ |
| A3 | **Navigation shell**: bottom nav (Record · Routes · Riders · Stats · Settings), route-detail back stack, top app bar + sync chip, chrome hidden on login/detail per spec. | ✅ |
| A4 | **DI + app state container**: dependency injection (Hilt or chosen DI) and the app-level state (`screen`, `authed`, `lang`, `theme`, `accent`, `units`) as unidirectional `StateFlow` UI state. | ✅ |
| A5 | **i18n string resources**: `strings.xml` for all 6 languages (pl/en/de/fr/cs/ru), copied from the `TRANS`/`T2` dicts in `MotoTracker.dc.html`; runtime language switch; metric↔imperial unit formatting. | ✅ |
| A6 | **Local persistence**: Room schema for routes/bikes/group/waves + settings (DataStore); an outbound **sync queue** table with retry state. Migrations. | ✅ |
| A7 | **GPStrack sync layer**: HTTP client to `http://192.168.1.145/gpstrack` (server address configurable); repository + sync-queue drainer with offline/online + auto-sync toggles. Client behind an interface (unit-testable with fakes). | ✅ |

## B. Screens & features (from README §Screens)

| #  | Item | Status |
|----|------|--------|
| B1 | **Login screen**: server address / e-mail / password + "sign in & sync" and "continue without account" (guest mode → local-only). | 🔬 |
| B2 | **Recording screen**: live map with growing track + position pulse, GPS/weather chips, wind rose, compass; speed/time/distance/altitude/fuel/lean tiles; start/pause/resume/finish controls; foreground location service; accelerometer-derived lean; save → route + toast (online/offline). | 🔬 |
| B3 | **Routes list**: summary tiles (count, total km), GPX import, per-route cards (track thumbnail, name, date · bike, sync/queue marker) → detail. | 🔬 |
| B4 | **Route detail**: map with start/end markers; 6 stat tiles; weather card (or "offline"); speed chart + elevation profile; "meetups on route" (BT waves); export/share + send-to-server. | 🔬 |
| B5 | **Riders**: riding group (add by phone), live feed (requires internet), Bluetooth "waves" (works offline). | 🔬 |
| B6 | **Stats**: 4 summary tiles; distance-per-month bar chart; riding-style summary (avg lean, avg speed, total ascent). | 🔬 |
| B7 | **Settings**: account, my motorcycles (add/select/status), appearance & language, server & sync, sync queue, Bluetooth broadcast profile, system & privacy (work-without-internet, GPS road correction, Android Auto), preferences (units, auto-pause, keep screen on). | 🔬 |
| B8 | **Export sheet + toasts**: GPX export, share link, send to GPStrack; confirmation toasts. | 🔬 |
| B9 | **Android Auto view**: glanceable recording screen (Car App Library templates) — big speed, time/distance, lean/altitude, oversized start/pause/stop; night/day themes. | 🔬 |

## C. Out of scope (NOT tasks)

- Real offline vector map tiles + a full offline map-matching engine (Valhalla/
  GraphHopper on downloaded OSM). Ship with an online map + the `gpsCorrect`
  option/seam; full offline routing is a later epic.
- iOS / cross-platform build.
- Server-side (GPStrack backend) changes — that lives in the MotoTracker `backend/`.
- Real Google/OAuth sign-in backend (the login screen targets the GPStrack server).

## D. Bugs / priority fixes

*(empty — the architect adds items here only for defects found against a `✅` feature)*
