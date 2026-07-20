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
