# GitHub CI + wersjonowanie + podpisane wydania — projekt (Faza 1)

**Data:** 2026-07-20
**Status:** zatwierdzony
**Repo:** `git@github.com:ArturKos/MotoTracker.git`

## Cel

Dodać do MotoTrackera automatyczne budowanie na GitHub Actions oraz jednoznaczne
wersjonowanie i podpisane wydania. To wspólny fundament pod dowolny kanał
dystrybucji z Fazy 2 (GitHub Releases + Obtainium / IzzyOnDroid / główne repo
F-Droid) — Faza 1 nie wybiera jeszcze kanału, tylko wytwarza podpisany artefakt.

## Kontekst i ustalenia

- **Struktura repo:** root `MotoTracker/` (tu `.git`), projekt Android w
  podkatalogu `Android/design_handoff_mototracker/`. Workflowy i skrypty muszą
  celować w ten podkatalog (`./gradlew` = `Android/design_handoff_mototracker/gradlew`).
- **Toolchain:** Java 21 uruchamia Gradle (projekt kompiluje do bytecode Java 17),
  Gradle 8.13, AGP 8.13.2, Kotlin 2.0.21, compileSdk/targetSdk 36, minSdk 26.
  Pluginy: hilt, ksp, compose, roborazzi.
- **Kwalifikacja F-Droid (potwierdzona):** brak Google Play Services / Firebase /
  jakichkolwiek proprietary blobów. Lokalizacja = framework `LocationManager`
  (klasa nazywa się `FusedLocationClientImpl`, ale NIE używa GMS), mapy = osmdroid
  (OSM), reszta AndroidX/Compose/Hilt/Room/DataStore/car.app = Apache-2.0.
  Aplikacja realnie kwalifikuje się do głównego repo F-Droid — brakuje tylko
  licencji FOSS (dodawana tu) i tagowanych wydań (dodawane tu).
- **Auth:** git push (workflowy, tagi) przez istniejący klucz SSH konta ArturKos.
  `gh` CLI NIE jest zalogowany → GitHub Secrets ustawiane ręcznie w UI.
- **Tożsamość commitów:** repo-local `Artur <R_E_D_O_X@wp.pl>`.
- **Stan wyjściowy:** brak `.github/workflows`, `versionCode=1`/`versionName="1.0"`
  niezmieniane, zero tagów, brak `LICENSE`.

## Komponenty

### 1. Walidacyjne CI — `.github/workflows/ci.yml`

- **Wyzwalacze:** `push` (wszystkie gałęzie) + `pull_request`, filtr ścieżek
  `Android/**` (pomija zmiany czysto-dokumentacyjne); `workflow_dispatch` (ręczny
  bieg testowy). `concurrency` z `cancel-in-progress` anuluje przedawnione biegi
  na tej samej gałęzi/ref.
- **Job (ubuntu-latest):**
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` — temurin 21
  3. `gradle/actions/setup-gradle@v4` — cache Gradle
  4. `sdkmanager` doinstaluje `platforms;android-36` + `build-tools;36.0.0`
     (asekuracja, gdyby runner nie miał SDK 36)
  5. `./gradlew testDebugUnitTest lintDebug assembleDebug`
     (`working-directory: Android/design_handoff_mototracker`)
- **Rola:** siatka bezpieczeństwa dla autonomicznej pętli `agent_workflow.py` —
  każdy push (także od Artura z pętli) dostaje test+lint+build; regresja typu
  „sekcja M / brak zapisu GPS" zapali się na czerwono zamiast przejść niezauważona.

### 2. Wydania — `.github/workflows/release.yml`

- **Wyzwalacz:** push tagu `v*` (np. `v1.0.0`).
- **Job (ubuntu-latest):**
  1. checkout tagu → setup Java 21 → setup-gradle → sdkmanager (jak wyżej)
  2. **Guardrail wersji:** wyłuskaj `versionName` z `app/build.gradle.kts` i sprawdź,
     że równa się tagowi bez prefiksu `v`; mismatch = fail workflowa.
  3. Odkoduj sekret `KEYSTORE_BASE64` → plik `.jks`; wyeksportuj ścieżkę jako
     `KEYSTORE_FILE`.
  4. `./gradlew assembleRelease` z `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/
     `KEY_PASSWORD` w env.
  5. `softprops/action-gh-release@v2` — utwórz GitHub Release dla tagu, auto-notatki,
     załącz `app-release.apk`.
- **Artefakt:** `Android/design_handoff_mototracker/app/build/outputs/apk/release/app-release.apk`
  (podpisany).

### 3. Podpisywanie — zmiana w `app/build.gradle.kts`

- Dodać `signingConfigs.create("release")` czytające z **env**:
  `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- `buildTypes.release.signingConfig`: gdy `KEYSTORE_FILE` obecne → podpis release;
  gdy nieobecne (lokalny dev, `assembleDebug`, CI walidacyjne) → fallback na
  `signingConfigs.getByName("debug")`. Dzięki temu nic się nie psuje bez sekretów.
- Nie zmieniamy `isMinifyEnabled` (zostaje `false`) — poza zakresem.
- **Klucz stały między wydaniami** — inny podpis = odmowa aktualizacji w
  Obtainium/IoD. Keystore generowany raz, backup obok baz
  (`mototracker-db-backups/`), NIE commitowany.

### 4. GitHub Secrets (ustawiane ręcznie w UI)

`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Workflow release dekoduje `KEYSTORE_BASE64` do pliku i podstawia jako `KEYSTORE_FILE`.
Wartości przygotowane przez asystenta do wklejenia (w tym base64 keystore'u).

### 5. Wersjonowanie — `scripts/bump-version.sh`

- `./scripts/bump-version.sh X.Y.Z` → wpisuje `versionName="X.Y.Z"` i
  `versionCode = X*10000 + Y*100 + Z` do `app/build.gradle.kts`.
- Flaga `--tag` → po edycji robi `git add` + commit + `git tag vX.Y.Z` (bez push;
  push tagu robi człowiek, co uruchamia workflow release).
- Walidacja wejścia: format `X.Y.Z`, `Y<100` i `Z<100` (żeby versionCode był
  monotoniczny), nowa wersja > obecnej.
- Pierwszy bump: `1 → 1.0.0` (versionCode `1 → 10000`, rośnie).
- Rationale: źródło samoopisowe (wersja w commicie na tagu) = idealne pod główne
  repo F-Droid, które buduje z tagu. Wydanie = świadoma decyzja człowieka, nie pętli.

### 6. `LICENSE` — GPL-3.0-or-later

Pełny tekst GPLv3 w root repo. Kompatybilny z zależnościami Apache-2.0.
Odblokowuje Fazę 2 (F-Droid/IzzyOnDroid wymagają licencji FOSS).
Bez nagłówków licencyjnych w plikach źródłowych (poza zakresem).

## Weryfikacja pipeline'u

- **CI:** `workflow_dispatch` (lub push gałęzi testowej) → zielony bieg w Actions
  (test + lint + assembleDebug).
- **Release:** `./scripts/bump-version.sh 1.0.0 --tag` → push tagu `v1.0.0` →
  w Releases pojawia się podpisany `app-release.apk`. Podpis weryfikowany
  `apksigner verify --print-certs`.

## Poza zakresem (YAGNI / Faza 2)

Google Play, MR z metadanymi do fdroiddata, `fastlane/metadata/android` (opisy,
screeny), ProGuard/minify, hardening reproducible-build, dodanie do Obtainium/IoD.

## Otwarta kwestia (nie blokuje Fazy 1)

Widoczność repo: dla głównego F-Droid źródło musi być **publiczne** (warunek Fazy 2).
Dla Fazy 1 bez znaczenia — podpisany APK w Releases działa i na prywatnym repo;
publiczne repo = darmowe minuty Actions.
