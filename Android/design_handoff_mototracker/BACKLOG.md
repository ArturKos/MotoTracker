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
| A8 | **Diagnostics logging infrastructure**: `RideDebugLogger` interface (`beginRide()` / `log(tag, message)` / `endRide()`) + `@Singleton` `FileRideDebugLogger` writing plain-text, ISO-8601-UTC, one-line-per-event logs to `context.getExternalFilesDir("ride-logs")/ride-<ts>.log`; writes serialized via a `Channel` on `Dispatchers.IO` (never blocks the 1 Hz recording loop); ALL I/O wrapped in try/catch so logging can never crash a ride; a true no-op when disabled (no file created, cheap early return). Add `debugLoggingEnabled: Boolean = false` to the settings model + `SettingsDataStore`/`WritableSettingsSource`, exposed in `SettingsUiState`. Register a `FileProvider` (`<provider>` in `AndroidManifest.xml` + `res/xml/file_paths.xml`) for later sharing (avoids `FileUriExposedException` on API 24+). Logs live in app-specific storage (not world-readable). Unit-test on a temp dir: enabled → file created + line format correct; disabled → no file, no-op. Depends on A6 (persistence). | ⬜ |
| A9 | **GPStrack authentication (infra, extends A7)**: add `login(server, email, password)` to the GPStrack client — **POST form-encoded credentials, capture the `Set-Cookie` session**, store it in a persisted cookie jar (DataStore), and attach the session cookie to all subsequent GPStrack requests; expose an auth/session state and handle 401 / expired session (surface re-login). Client stays behind the A7 interface (unit-testable with fakes). Unit-test on a fake HTTP client: form-body encoding, `Set-Cookie` capture, cookie attached on the next request, 401 handling. Depends on A7. | ⬜ |
| A10 | **GPS-track correction (server-side OSRM map-matching, infra)**: `GpsCorrectionClient` interface (with a fake for tests) + `OsrmGpsCorrectionClient` calling OSRM `/match/v1/driving/{lon,lat;…}?geometries=geojson&overview=full&tidy=true&gaps=split` (+ `radiuses`/`timestamps`), `lon,lat` formatting, **chunking long rides (~100 pts/request)**, parsing `matchings[].geometry`. **Quality gate (pure, unit-testable):** accept the corrected trace only when `confidence ≥ threshold` AND matched-point fraction ≥ threshold (thresholds configurable); on `NoMatch`/partial/low confidence → **keep the raw trace** and set status accordingly. **Data model:** each route stores `rawTrace` (immutable source of truth, NEVER deleted), a nullable `correctedTrace`, `correctionStatus` (none / queued / low-confidence / done) and `confidence`. **Correction queue** (route id + state + retry) that drains automatically only when the OSRM server is reachable — mirrors the A6/A7 sync-queue; **LAN-only, no port-forward** (home OSRM stays closed to the internet). Configurable OSRM base URL in settings (DataStore; default `http://192.168.1.142:5001`). Unit-test: request building (coords/chunking/params), response parsing (GeoJSON→points), quality-gate verdicts, queue retry — all with a fake HTTP client. Depends on A6, A7. | ⬜ |

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
| B10 | **Diagnostics UI + ride-logging wiring** (needs A8): in Settings → System & privacy add a **"Diagnostyka"** group with (a) a toggle *"Zapisuj logi diagnostyczne podczas jazdy"* bound to the A8 `debugLoggingEnabled` setting (default off), (b) a *"Udostępnij log"* row that shares the latest ride log via `FileProvider` + `Intent.ACTION_SEND`, and (c) a *"Wyczyść logi"* row that deletes all files in `ride-logs`, with a used-space counter. Wire `RideDebugLogger` into `RecordingViewModel`: `beginRide()` on start; log GPS samples, lean, weather calls, lifecycle (pause/resume/finish) and caught errors; `endRide()` on finish. All new user-facing strings in all 6 languages (pl/en/de/fr/cs/ru). Unit-test: toggle persistence (`SettingsViewModel`, fake source), clear-deletes-files, and the share-`Intent` URI builder (thin wrapper). `🔬`: real share sheet + on-device file writing during a live ride. | ⬜ |
| B11 | **Real online map (osmdroid) — replaces the schematic placeholder** (fixes "map doesn't render on the Record screen"). Add the osmdroid dependency; replace `RecordingMapPlaceholder` (a `Canvas` sine-wave stand-in) with a real `MapView` hosted via `AndroidView` with proper lifecycle handling (`onResume`/`onPause`/`onDetach`): a live growing track polyline + position marker on **Record** (B2), and a route polyline + start/end markers on **Route detail** (B4). Keep the `gpsCorrect` seam. Uses the already-declared `INTERNET` permission; raster OSM tiles cached on disk (an offline tile pack remains a future epic — still section C). Unit-test the testable seam: GPS→screen projection / polyline building. `🔬`: actual map rendering + tiles on-device. | ⬜ |
| B12 | **Wire Login to real GPStrack auth** (needs A9): connect the Login screen (B1) to A9 — "sign in & sync" calls `login()`, on success sets `authed = true` and drains the sync queue, on failure shows an inline error; "continue without account" (guest / local-only) stays. The server-address field feeds A7/A9. Unit-test `LoginViewModel` states (idle/loading/success/error) with a fake auth client. All new user-facing strings in all 6 languages (pl/en/de/fr/cs/ru). `🔬`: real sign-in against a live GPStrack server on-device. | ⬜ |
| B13 | **GPS-correction UX on Route detail** (needs A10): a **Raw | Corrected** segmented toggle (Corrected disabled until it exists; default to corrected when present, else raw); a **"Popraw teraz"** button that enqueues/attempts correction on demand (same A10 path — retry a low-confidence result or run once home on WiFi); and **"Usuń poprawioną trasę"** (safe, reversible — re-runnable from raw). The **raw trace is never deletable** (permanent source of truth); removing a whole ride stays in the Routes list (B3). Correction auto-runs on ride finish when the `gpsCorrect` toggle (Settings) is on. Surface correction status/confidence (e.g. "niska pewność"). All new user-facing strings in all 6 languages (pl/en/de/fr/cs/ru). Unit-test the detail VM: view-toggle state, manual-trigger enqueues, delete-corrected clears only the corrected geometry (raw untouched). `🔬`: real OSRM round-trip + on-device rendering. | ⬜ |

## C. Out of scope (NOT tasks)

- **On-device / fully offline** vector map tiles + an on-device offline map-matching
  engine (Valhalla/GraphHopper on downloaded OSM) — later epic. (Online map = **B11**
  via osmdroid; server-side GPS correction / `gpsCorrect` = **A10 + B13** via the home
  OSRM. Only the on-device offline engine remains out of scope.)
- iOS / cross-platform build.
- Server-side (GPStrack backend) changes — that lives in the MotoTracker `backend/`.
- **Third-party / Google OAuth** sign-in — out of scope. (Real e-mail/password auth
  against the GPStrack server IS now in scope: **A9 + B12**.)

## D. Bugs / priority fixes

| #  | Item | Status |
|----|------|--------|
| D1 | **Crash on "start ride" — missing runtime location permission.** Tapping *Rozpocznij jazdę* on the Recording screen (B2) crashes the app with `java.lang.SecurityException: "gps" location provider requires ACCESS_FINE_LOCATION permission`. Root cause: `FusedLocationClientImpl.locationUpdates` (`app/.../data/location/LocationClient.kt:65`, marked `@SuppressLint("MissingPermission")`) calls `LocationManager.requestLocationUpdates(GPS_PROVIDER, …)` with **no runtime permission grant**; `RecordingViewModel.startLocationUpdates()` (`RecordingViewModel.kt:188`) collects that flow inside a `viewModelScope.launch` where the `SecurityException` is uncaught → app dies. The manifest declares `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` but **no runtime-permission request flow exists anywhere** (the only `rememberLauncherForActivityResult` is GPX import in `RoutesScreen`). **Repro:** Huawei P20 Lite (ANE-LX1, Android 9 / API 28), fresh install → tap start ride. Did NOT reproduce on the emulator (permission pre-granted / step skipped). **Fix direction:** request `ACCESS_FINE_LOCATION` (and `POST_NOTIFICATIONS` on API 33+) before recording — gate `startLocationUpdates()` behind a granted permission via a Compose permission launcher (`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` or Accompanist Permissions), show a rationale/denied state on the Recording screen, AND defensively wrap the `callbackFlow` body in a `try/catch (SecurityException)` that `close(e)`s the flow so a revoked/denied grant degrades gracefully instead of crashing. Add a unit test for the SecurityException-→-flow-closes seam. | ⬜ |
| D2 | **No runtime-permission handling anywhere (umbrella; D1 is its crash symptom).** The app never asks the user for any runtime permission — it only *declares* them in the manifest. On API 23+ declared ≠ granted, so every permission-gated feature silently fails or crashes until the user grants manually in system Settings. Concrete gaps: (a) **`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`** — never requested (causes D1 crash on Record). (b) **`POST_NOTIFICATIONS`** (API 33+) — declared but never requested → the foreground-service recording notification is silently suppressed on Android 13+. (c) **Bluetooth for the "waves" feature (B5)** — `BLUETOOTH_SCAN`/`BLUETOOTH_ADVERTISE`/`BLUETOOTH_CONNECT` are **not even declared** in the manifest and not requested (BLE is still a data-only seam today, so this bites once BLE advertise/scan becomes real). **Fix direction:** add a small central permission layer (a reusable Composable/helper) that requests the right permissions at the right entry points — location when entering Record, notifications on first foreground-service use, Bluetooth when opening Waves — each with a rationale + graceful "denied" UI state; declare the missing Bluetooth perms (with `neverForLocation` flag on `BLUETOOTH_SCAN` where applicable). Unit-test the permission-state → action gating; the actual system dialogs are `🔬` (on-device). | ⬜ |
| D3 | **Add-motorcycle has no input form — name/year/plate/status can't be set.** In Settings → My motorcycles, the *Dodaj motocykl* button (`SettingsScreen.kt:125`) calls `viewModel.addBike(name = <"Nowy motocykl">, year = 2024, plate = "")` with **hardcoded default values and no dialog/screen to enter data**. There is also **no edit path**: `SettingsViewModel` exposes only `selectBike` and `addBike` (no `updateBike`/`editBike`), and `BikeRow`'s only gesture is `onSelect` (mark as current) — so a bike's name/year/plate/status can never be entered or changed. The domain model `Bike(name, year, plate, status)` supports all fields; the UI just never collects them. **Repro:** Settings → My motorcycles → Add → a bike named "Nowy motocykl / 2024" appears with no way to rename or edit. **Fix direction:** add an add/edit-bike dialog (or sub-screen) with fields for name, year, plate and an active/sold status toggle (reuse the existing `OutlinedTextField`/`BcTextField` styling); wire `addBike` to the form values and add `updateBike(id, …)` to the VM; open the form both from the Add button and by tapping a `BikeRow` (or an explicit edit affordance). Unit-test add/update/validation in `SettingsViewModel`; the dialog rendering is `🔬` (on-device). | ⬜ |
| D4 | **Changing theme / language / accent / units in Settings has no visible effect.** Two root causes. (1) **`AppStateViewModel` is resolved in two different Compose/Hilt scopes:** `MainActivity` obtains it via `hiltViewModel()` at the Activity root (this instance drives `MotoTrackerTheme(theme, accent)`), while `SettingsScreen` — inside the `NavHost` — obtains it via `hiltViewModel()` scoped to the Settings `NavBackStackEntry`. These are **different instances**, so `appStateVm.setTheme/setAccent/setUnits(...)` from Settings mutate a `StateFlow` the theme never observes → nothing re-renders. (2) **Language can't apply either:** `setLanguage` → `LocaleController.applyLanguage` → `AppCompatDelegate.setApplicationLocales(...)`, but `MainActivity` extends `ComponentActivity`, **not `AppCompatActivity`**, and `androidx.appcompat.app.AppLocalesMetadataHolderService` is not registered → the per-app locale is never applied/recreated. **Repro:** Settings → change theme or language → UI unchanged. **Fix direction:** (a) share ONE app-scoped `AppStateViewModel` — hoist the instance from `MainActivity` and pass it (or its state + callbacks) into `MotoApp`/`SettingsScreen`, or resolve it against the Activity `ViewModelStoreOwner`, so Settings drives the same instance the theme observes; (b) make `MainActivity` an `AppCompatActivity` (Compose still works) and register the AppCompat locales metadata service so `setApplicationLocales` takes effect. Related debt: `AppStateViewModel` is still in-memory (its own KDoc says persistence is deferred to A6, but A6 is `✅`) — settings won't survive restart until DataStore-backed; wire that here or track as follow-up. Unit-test: setters update the observed state, and `LocaleController` is invoked with the correct tag. `🔬`: actual on-device theme repaint + locale switch. | ⬜ |

*(section format: the architect ticks `✅` once fixed + unit-tested + clean `assembleDebug`/`lintDebug`; on-device confirmation of the permission dialog is `🔬`.)*
