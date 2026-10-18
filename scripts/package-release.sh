#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
ALLOW_DIRTY=false
if [[ "${1:-}" == --allow-dirty ]]; then
  ALLOW_DIRTY=true
  shift
fi
[[ $# == 0 ]] || {
  echo 'Usage: package-release.sh [--allow-dirty]' >&2
  exit 1
}
SOURCE_SHA="$(git rev-parse HEAD)"
DIRTY=false
if [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then DIRTY=true; fi
if [[ "$DIRTY" == true && "$ALLOW_DIRTY" != true ]]; then
  echo 'Release packaging requires a clean checkout. Use --allow-dirty only for local verification.' >&2
  exit 1
fi
JAR="$ROOT/backend/build/libs/mealplan-0.1.0.jar"
[[ -f "$JAR" ]] || {
  echo 'Build the executable JAR first.' >&2
  exit 1
}
exec java -jar "$JAR" package-release --root "$ROOT" --jar "$JAR" --content "$ROOT/content" --commit "$SOURCE_SHA" --dirty "$DIRTY" --output "$ROOT/build/releases/mealplan-$SOURCE_SHA.zip"
