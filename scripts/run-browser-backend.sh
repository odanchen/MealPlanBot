#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
STATE="$(mktemp -d "${TMPDIR:-/tmp}/mealplan-browser.XXXXXX")"
# Browser runs use disposable SQLite and test-classpath identity injection.
# The packaged/local application retains its single fixture identity.
APP_PID=""
cleanup() {
  if [[ -n "$APP_PID" ]]; then kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true; fi
  rm -rf "$STATE"
}
trap cleanup EXIT
trap 'exit 0' TERM INT
./backend/gradlew -p backend prepareBrowserTests >/dev/null
java --enable-native-access=ALL-UNNAMED -cp "$(cat backend/build/browser-test-classpath.txt)" app.mealplan.support.BrowserTestApplication --spring.profiles.active=local,browser-test --mealplan.database-path="$STATE/.local/mealplan/app.sqlite" &
APP_PID=$!
wait "$APP_PID"
