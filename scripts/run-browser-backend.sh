#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
STATE="$(mktemp -d "${TMPDIR:-/tmp}/mealplan-browser.XXXXXX")"
# Browser runs have their own disposable database and the same local fixture user.
APP_PID=""
cleanup() {
  if [[ -n "$APP_PID" ]]; then kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true; fi
  rm -rf "$STATE"
}
trap cleanup EXIT
trap 'exit 0' TERM INT
java --enable-native-access=ALL-UNNAMED -jar backend/build/libs/mealplan-0.1.0.jar --spring.profiles.active=local --mealplan.database-path="$STATE/.local/mealplan/app.sqlite" &
APP_PID=$!
wait "$APP_PID"
