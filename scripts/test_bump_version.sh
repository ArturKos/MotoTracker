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

# rejects leading zeros in version components
if "$HERE/bump-version.sh" 1.2.08 2>/dev/null; then echo "FAIL: powinien odrzucić 1.2.08 (wiodące zero)"; exit 1; fi

# rejects unrecognized second argument
if "$HERE/bump-version.sh" 1.3.0 --tagg 2>/dev/null; then echo "FAIL: powinien odrzucić nieznany argument"; exit 1; fi

# test --tag flow: commit + tag creation
REPO="$TMP/repo"
mkdir "$REPO"
cd "$REPO"
git init
git config user.email "t@t"
git config user.name "t"

cat > build.gradle.kts <<'FIXTURE'
        versionCode = 1
        versionName = "1.0"
FIXTURE

git add build.gradle.kts
git commit -m "init"

# run bump-version with --tag from inside repo
cd "$REPO"
BUMP_GRADLE_FILE="$REPO/build.gradle.kts" "$HERE/bump-version.sh" 2.0.0 --tag

# assert tag exists
git tag --list v2.0.0 | grep -q v2.0.0 || { echo "FAIL: tag v2.0.0 nie istnieje"; exit 1; }

# assert new commit created with correct subject
git log -1 --pretty=%B | grep -q "release: v2.0.0" || { echo "FAIL: commit message invalid"; exit 1; }

# assert no remote exists and working tree is clean
[ -z "$(git remote -v)" ] || { echo "FAIL: repo powinien być bez remote"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "FAIL: working tree powinien być czysty"; exit 1; }

echo "PASS"
