# GitHub CI + wersjonowanie + podpisane wydania — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dodać walidacyjne CI, wersjonowanie z tagów i podpisane wydania (GitHub Release) dla MotoTrackera — wspólny fundament pod dowolny kanał dystrybucji z Fazy 2.

**Architecture:** Dwa workflowy GitHub Actions (walidacja na push/PR; wydanie na tag `v*`), konfiguracja podpisu w `build.gradle.kts` czytana z env (fallback na debug), skrypt `bump-version.sh` do jednoznacznego bumpa wersji, plik `LICENSE` (GPL-3.0). Klucz podpisujący trzymany w GitHub Secrets, generowany raz i backupowany poza repo.

**Tech Stack:** GitHub Actions, Gradle (Kotlin DSL) 8.13 / AGP 8.13.2, JDK 21 (temurin), Android SDK 36, bash, keytool/apksigner.

## Global Constraints

- Repo root: `MotoTracker/` (tu `.git`); projekt Android w `Android/design_handoff_mototracker/`. Workflowy = w `MotoTracker/.github/workflows/`; gradle wołany z podkatalogu.
- Toolchain (wartości dokładne): JDK **21** temurin uruchamia Gradle; projekt kompiluje do bytecode **17**; Gradle **8.13**; AGP **8.13.2**; compileSdk/targetSdk **36**; minSdk **26**.
- `versionCode` = `major*10000 + minor*100 + patch`; wymóg: `minor < 100`, `patch < 100`, nowy `versionCode` > obecnego.
- Tag wydania: `vX.Y.Z`; `versionName` w `build.gradle.kts` musi == tag bez `v`.
- Klucz podpisujący **stały** między wydaniami; keystore **nie** trafia do gita.
- GitHub Secrets (ustawiane ręcznie w UI, `gh` niezalogowany): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Tożsamość commitów: repo-local `Artur <R_E_D_O_X@wp.pl>` (już ustawione).
- Licencja: **GPL-3.0-or-later**.

## File Structure

- Create: `LICENSE` — tekst GPLv3 (root repo).
- Create: `scripts/bump-version.sh` — bump `versionName`/`versionCode` + opcjonalnie commit/tag.
- Create: `scripts/test_bump_version.sh` — test skryptu bumpa (bash, na fixture).
- Modify: `Android/design_handoff_mototracker/app/build.gradle.kts` — `signingConfigs` + `buildTypes.release.signingConfig`.
- Create: `.github/workflows/ci.yml` — walidacja (test+lint+assembleDebug).
- Create: `.github/workflows/release.yml` — wydanie na tag (assembleRelease + GitHub Release).
- Operacyjne (poza gitem): `mototracker-release.jks` (backup w `/home/akos/messiah/mototracker-db-backups/`), 4 GitHub Secrets.

---

### Task 1: LICENSE (GPL-3.0-or-later)

**Files:**
- Create: `LICENSE`

**Interfaces:**
- Consumes: nic.
- Produces: plik `LICENSE` w root — warunek Fazy 2 (F-Droid/IoD).

- [ ] **Step 1: Pobierz kanoniczny tekst GPLv3**

Run (z root repo):
```bash
cd /home/akos/messiah/MotoTracker
curl -fsSL https://www.gnu.org/licenses/gpl-3.0.txt -o LICENSE
```
Jeśli brak sieci: skopiuj plik z systemu — `cp /usr/share/common-licenses/GPL-3 LICENSE` (typowa ścieżka na Debian/Ubuntu).

- [ ] **Step 2: Zweryfikuj zawartość**

Run:
```bash
head -1 LICENSE && wc -l LICENSE
```
Expected: pierwsza linia zawiera `GNU GENERAL PUBLIC LICENSE`, plik ma ~674 linii (nie 0).

- [ ] **Step 3: Commit**

```bash
git add LICENSE
git commit -m "chore: dodaj licencję GPL-3.0-or-later"
```

---

### Task 2: Skrypt bump-version.sh (TDD)

**Files:**
- Create: `scripts/bump-version.sh`
- Test: `scripts/test_bump_version.sh`

**Interfaces:**
- Consumes: `Android/design_handoff_mototracker/app/build.gradle.kts` (pola `versionCode`/`versionName`). Ścieżkę można nadpisać env `BUMP_GRADLE_FILE` (do testów).
- Produces: CLI `bump-version.sh X.Y.Z [--tag]` — ustala `versionCode = X*10000+Y*100+Z` i `versionName="X.Y.Z"`; `--tag` robi commit + `git tag vX.Y.Z` (bez push).

- [ ] **Step 1: Napisz test (najpierw failuje — skryptu nie ma)**

Create `scripts/test_bump_version.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/build.gradle.kts" <<'EOF'
        versionCode = 1
        versionName = "1.0"
EOF

export BUMP_GRADLE_FILE="$TMP/build.gradle.kts"

# happy path: 1.2.3 -> code 10203, name "1.2.3"
"$HERE/bump-version.sh" 1.2.3
grep -q 'versionCode = 10203' "$TMP/build.gradle.kts" || { echo "FAIL: versionCode"; exit 1; }
grep -q 'versionName = "1.2.3"' "$TMP/build.gradle.kts" || { echo "FAIL: versionName"; exit 1; }

# rejects non-semver
if "$HERE/bump-version.sh" 1.2 2>/dev/null; then echo "FAIL: powinien odrzucić 1.2"; exit 1; fi

# rejects non-increasing versionCode (10203 already set; 1.0.0 -> 10000 < 10203)
if "$HERE/bump-version.sh" 1.0.0 2>/dev/null; then echo "FAIL: powinien odrzucić nie-rosnący code"; exit 1; fi

# rejects minor/patch >= 100
if "$HERE/bump-version.sh" 1.100.0 2>/dev/null; then echo "FAIL: powinien odrzucić minor>=100"; exit 1; fi

echo "PASS"
```

- [ ] **Step 2: Uruchom test — ma failować (brak skryptu)**

Run:
```bash
chmod +x scripts/test_bump_version.sh && ./scripts/test_bump_version.sh
```
Expected: FAIL — `bump-version.sh: No such file or directory`.

- [ ] **Step 3: Napisz skrypt**

Create `scripts/bump-version.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail

DEFAULT_GRADLE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/Android/design_handoff_mototracker/app/build.gradle.kts"
GRADLE_FILE="${BUMP_GRADLE_FILE:-$DEFAULT_GRADLE}"

usage() { echo "usage: $(basename "$0") X.Y.Z [--tag]" >&2; exit 2; }

VERSION="${1:-}"
TAG_FLAG="${2:-}"
[ -n "$VERSION" ] || usage

if ! [[ "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "error: wersja musi być X.Y.Z (dostałem '$VERSION')" >&2; exit 1
fi
MAJOR="${BASH_REMATCH[1]}"; MINOR="${BASH_REMATCH[2]}"; PATCH="${BASH_REMATCH[3]}"

if [ "$MINOR" -ge 100 ] || [ "$PATCH" -ge 100 ]; then
  echo "error: minor i patch muszą być < 100 (schemat versionCode)" >&2; exit 1
fi
NEW_CODE=$(( MAJOR*10000 + MINOR*100 + PATCH ))

[ -f "$GRADLE_FILE" ] || { echo "error: brak $GRADLE_FILE" >&2; exit 1; }
CUR_CODE="$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE_FILE")"
if [ "$NEW_CODE" -le "$CUR_CODE" ]; then
  echo "error: nowy versionCode $NEW_CODE nie jest większy od obecnego $CUR_CODE" >&2; exit 1
fi

sed -i -E "s/(versionCode\s*=\s*)[0-9]+/\1$NEW_CODE/" "$GRADLE_FILE"
sed -i -E "s/(versionName\s*=\s*\")[^\"]+(\")/\1$VERSION\2/" "$GRADLE_FILE"

echo "bumped: versionName=$VERSION versionCode=$NEW_CODE ($GRADLE_FILE)"

if [ "$TAG_FLAG" = "--tag" ]; then
  git add "$GRADLE_FILE"
  git commit -m "release: v$VERSION (versionCode $NEW_CODE)"
  git tag "v$VERSION"
  echo "commit + tag v$VERSION gotowe — wypchnij: git push && git push origin v$VERSION"
fi
```

- [ ] **Step 4: Uruchom test — ma przejść**

Run:
```bash
chmod +x scripts/bump-version.sh && ./scripts/test_bump_version.sh
```
Expected: `PASS`.

- [ ] **Step 5: Commit**

```bash
git add scripts/bump-version.sh scripts/test_bump_version.sh
git commit -m "feat: skrypt bump-version.sh + test (wersjonowanie z tagów)"
```

---

### Task 3: Konfiguracja podpisu w build.gradle.kts

**Files:**
- Modify: `Android/design_handoff_mototracker/app/build.gradle.kts` (blok `android { ... }`, obecnie linie ~12–33)

**Interfaces:**
- Consumes: env `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (Task 4 / release.yml).
- Produces: `buildTypes.release` podpisany kluczem release gdy `KEYSTORE_FILE` obecne; inaczej fallback na podpis `debug` (żeby lokalny build i CI walidacyjne działały bez sekretów).

- [ ] **Step 1: Dodaj `signingConfigs` przed `buildTypes`**

W `build.gradle.kts`, wewnątrz `android { ... }`, tuż po zamknięciu `defaultConfig { ... }` (po linii `}` kończącej defaultConfig, przed `buildTypes {`), wstaw:
```kotlin
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
```

- [ ] **Step 2: Wskaż signingConfig w release**

Zamień istniejący blok:
```kotlin
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```
na:
```kotlin
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }
```

- [ ] **Step 3: Zweryfikuj — build release bez sekretów spada na debug i przechodzi**

Run:
```bash
cd Android/design_handoff_mototracker
./gradlew assembleRelease --stacktrace
```
Expected: `BUILD SUCCESSFUL`; powstaje `app/build/outputs/apk/release/app-release.apk` (podpisany kluczem debug — fallback). Brak błędu o braku signingConfig.

- [ ] **Step 4: Zweryfikuj — debug build nienaruszony**

Run:
```bash
./gradlew assembleDebug --stacktrace
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/akos/messiah/MotoTracker
git add Android/design_handoff_mototracker/app/build.gradle.kts
git commit -m "build: podpis release z env (fallback na debug bez sekretów)"
```

---

### Task 4: Wygeneruj keystore i przygotuj GitHub Secrets

**Files:**
- Operacyjne (poza gitem): `mototracker-release.jks`, backup w `/home/akos/messiah/mototracker-db-backups/`, 4 sekrety w GitHub UI.

**Interfaces:**
- Consumes: nic.
- Produces: keystore + wartości `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` do wklejenia w GitHub Secrets (używane przez release.yml, Task 6).

- [ ] **Step 1: Wygeneruj hasła i keystore**

Run (w katalogu roboczym poza repo, np. scratchpad):
```bash
STOREPASS="$(openssl rand -base64 24)"
KEYPASS="$STOREPASS"
ALIAS="mototracker"
keytool -genkeypair -v \
  -keystore mototracker-release.jks \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$STOREPASS" -keypass "$KEYPASS" \
  -dname "CN=MotoTracker, O=ArturKos, C=PL"
echo "KEYSTORE_PASSWORD=$STOREPASS"
echo "KEY_PASSWORD=$KEYPASS"
echo "KEY_ALIAS=$ALIAS"
```
Zapisz wypisane hasła (będą potrzebne w Step 3).

- [ ] **Step 2: Zweryfikuj keystore i policz base64**

Run:
```bash
keytool -list -keystore mototracker-release.jks -storepass "$STOREPASS"
base64 -w0 mototracker-release.jks > keystore.b64
wc -c keystore.b64
```
Expected: `keytool -list` pokazuje wpis aliasu `mototracker, PrivateKeyEntry`; `keystore.b64` niepusty.

- [ ] **Step 3: Ustaw 4 sekrety w GitHub UI**

W przeglądarce: `github.com/ArturKos/MotoTracker` → Settings → Secrets and variables → Actions → New repository secret. Dodaj cztery:
- `KEYSTORE_BASE64` = zawartość `keystore.b64`
- `KEYSTORE_PASSWORD` = wartość ze Step 1
- `KEY_PASSWORD` = wartość ze Step 1
- `KEY_ALIAS` = `mototracker`

(Uwaga: `gh` niezalogowany, więc CLI odpada — ustawiamy ręcznie.)

- [ ] **Step 4: Backup keystore poza repo (KRYTYCZNE)**

Run:
```bash
cp mototracker-release.jks /home/akos/messiah/mototracker-db-backups/mototracker-release.jks
ls -l /home/akos/messiah/mototracker-db-backups/mototracker-release.jks
```
Expected: plik skopiowany. Utrata tego klucza = użytkownicy nie zaktualizują appki (inny podpis). NIE commituj `.jks` ani `keystore.b64` do repo.

- [ ] **Step 5: Zweryfikuj, że keystore nie wejdzie do gita**

Run (z root repo):
```bash
cd /home/akos/messiah/MotoTracker
git status --porcelain | grep -E 'release\.jks|keystore\.b64' && echo "UWAGA: usuń te pliki z repo!" || echo "OK: keystore poza repo"
```
Expected: `OK: keystore poza repo` (pliki powstały poza drzewem repo).

---

### Task 5: Workflow walidacyjny ci.yml

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: nic (brak sekretów).
- Produces: bieg CI (test+lint+assembleDebug) na push/PR/`workflow_dispatch`.

- [ ] **Step 1: Utwórz workflow**

Create `.github/workflows/ci.yml`:
```yaml
name: CI

on:
  push:
    paths:
      - 'Android/**'
      - '.github/workflows/ci.yml'
  pull_request:
    paths:
      - 'Android/**'
      - '.github/workflows/ci.yml'
  workflow_dispatch:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: Android/design_handoff_mototracker
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - uses: gradle/actions/setup-gradle@v4

      - name: Doinstaluj pakiety Android SDK
        run: yes | sdkmanager "platforms;android-36" "build-tools;36.0.0" >/dev/null || true

      - name: Test + lint + assembleDebug
        run: ./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
```

- [ ] **Step 2: Zwaliduj składnię YAML lokalnie**

Run (z root repo):
```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('yaml ok')"
```
Expected: `yaml ok`.

- [ ] **Step 3: Commit i push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: workflow walidacyjny (test + lint + assembleDebug)"
git push
```

- [ ] **Step 4: Odpal ręcznie i sprawdź, że zielony**

W GitHub UI: Actions → „CI" → Run workflow (gałąź `main`). Poczekaj na wynik.
Expected: bieg kończy się zielony (job `build` PASS). Jeśli czerwony na braku SDK 36 — potwierdź w logu, że krok sdkmanager się wykonał; runner ubuntu-latest ma SDK preinstalowane, krok jest asekuracją.

---

### Task 6: Workflow wydania release.yml + test end-to-end

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: sekrety z Task 4; `signingConfig` z Task 3; `versionName` z `build.gradle.kts`.
- Produces: GitHub Release na tag `v*` z załączonym podpisanym `app-release.apk`.

- [ ] **Step 1: Utwórz workflow**

Create `.github/workflows/release.yml`:
```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - uses: gradle/actions/setup-gradle@v4

      - name: Doinstaluj pakiety Android SDK
        run: yes | sdkmanager "platforms;android-36" "build-tools;36.0.0" >/dev/null || true

      - name: Sprawdź zgodność tag ↔ versionName
        run: |
          TAG="${GITHUB_REF_NAME#v}"
          VN="$(grep -oP 'versionName\s*=\s*"\K[^"]+' Android/design_handoff_mototracker/app/build.gradle.kts)"
          echo "tag=$TAG versionName=$VN"
          if [ "$TAG" != "$VN" ]; then
            echo "::error::tag '$TAG' != versionName '$VN'"; exit 1
          fi

      - name: Odkoduj keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > "${{ runner.temp }}/release.jks"

      - name: assembleRelease (podpisany)
        working-directory: Android/design_handoff_mototracker
        env:
          KEYSTORE_FILE: ${{ runner.temp }}/release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew assembleRelease --stacktrace

      - name: Opublikuj GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: Android/design_handoff_mototracker/app/build/outputs/apk/release/app-release.apk
          generate_release_notes: true
```

- [ ] **Step 2: Zwaliduj składnię YAML lokalnie**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml')); print('yaml ok')"
```
Expected: `yaml ok`.

- [ ] **Step 3: Commit i push workflowa**

```bash
git add .github/workflows/release.yml
git commit -m "ci: workflow wydania (assembleRelease podpisany + GitHub Release)"
git push
```

- [ ] **Step 4: Wytnij pierwsze wydanie 1.0.0 i wypchnij tag**

Run (z root repo; wymaga Task 2 i ustawionych sekretów z Task 4):
```bash
./scripts/bump-version.sh 1.0.0 --tag
git push && git push origin v1.0.0
```
Expected: commit `release: v1.0.0 (versionCode 10000)` + tag `v1.0.0` wypchnięte; workflow „Release" startuje.

- [ ] **Step 5: Zweryfikuj Release i podpis APK**

W GitHub UI: Releases → `v1.0.0` ma załączony `app-release.apk`. Pobierz i sprawdź podpis:
```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app-release.apk
```
Expected: `Verifies`; certyfikat = `CN=MotoTracker, O=ArturKos` (klucz release, NIE „Android Debug"). To potwierdza, że sekrety i podpis działają end-to-end.

---

## Self-Review

**Spec coverage:**
- Walidacyjne CI (§1) → Task 5. ✔
- Wydania na tag (§2) → Task 6. ✔
- Podpis w build.gradle.kts (§3) → Task 3. ✔
- GitHub Secrets (§4) → Task 4. ✔
- Wersjonowanie / bump-version.sh (§5) → Task 2. ✔
- LICENSE GPL-3.0 (§6) → Task 1. ✔
- Weryfikacja pipeline'u (CI dispatch, release+apksigner) → Task 5 Step 4, Task 6 Step 5. ✔
- Guardrail tag↔versionName → Task 6 Step 1 (krok „Sprawdź zgodność"). ✔

**Placeholder scan:** brak TBD/TODO; każdy krok kodu ma pełną treść. ✔

**Type/nazwy consistency:** env `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` spójne w build.gradle.kts (Task 3), sekretach (Task 4) i release.yml (Task 6). Ścieżka APK `app/build/outputs/apk/release/app-release.apk` spójna. `versionCode` schemat identyczny w bump-version.sh i Global Constraints. ✔

**Kolejność/zależności:** Task 3 (podpis) i Task 4 (sekrety) muszą być przed Task 6 Step 4 (wytnięcie wydania). Task 2 przed Task 6 Step 4. Task 1 i Task 5 niezależne. Wszystkie zależności odzwierciedlone w numeracji.
