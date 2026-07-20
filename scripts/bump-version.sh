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
