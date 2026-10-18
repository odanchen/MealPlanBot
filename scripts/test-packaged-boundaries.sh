#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
PID=
cleanup() {
  if [[ -n "$PID" ]]; then
    kill "$PID" 2> /dev/null || true
    wait "$PID" 2> /dev/null || true
  fi
  rm -rf -- "$WORK"
}
trap cleanup EXIT
JAR="$ROOT/backend/build/libs/mealplan-0.1.0.jar"
if unzip -Z1 "$JAR" | grep -E 'BOOT-INF/classes/.*(BrowserTest|TestPrincipal|/__test)' > "$WORK/unexpected"; then
  echo 'Test infrastructure found in release.' >&2
  exit 1
fi
GOOGLE_CLIENT_ID=packaged-test-client GOOGLE_CLIENT_SECRET=packaged-test-secret java --enable-native-access=ALL-UNNAMED -jar "$JAR" --spring.profiles.active=pi --mealplan.database-path="$WORK/app.sqlite" --mealplan.public-base-url=https://packaged.example.test --mealplan.content-path="$ROOT/content" --mealplan.commit=packaged-boundary-test > "$WORK/app.log" 2>&1 &
PID=$!
READY=false
for ATTEMPT in {1..60}; do
  if curl -fsS http://127.0.0.1:8000/health > "$WORK/health.json" 2> /dev/null; then
    READY=true
    break
  fi
  kill -0 "$PID"
  sleep 0.5
done
[[ "$READY" == true ]]
jq -e '.status=="ready" and .commit=="packaged-boundary-test"' "$WORK/health.json" > /dev/null
[[ "$(curl -s -o "$WORK/private.json" -D "$WORK/headers" -w '%{http_code}' -H 'X-Test-Principal: boundary-private-sentinel' http://127.0.0.1:8000/api/lists)" == 401 ]]
grep -qi '^Cache-Control: no-store' "$WORK/headers"
[[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{"name":"private-boundary-sentinel"}' http://127.0.0.1:8000/api/lists)" == 403 ]]
[[ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8000/api/recipes/1)" == 404 ]]
[[ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8000/__test/advance)" == 401 ]]
curl -fsS http://127.0.0.1:8000/ > "$WORK/public.html"
grep -q '<div id="root"' "$WORK/public.html"
if grep -qE 'boundary-private-sentinel|private-boundary-sentinel|packaged-test-secret' "$WORK/app.log"; then
  echo 'Sensitive request material reached logs.' >&2
  exit 1
fi
kill "$PID"
wait "$PID" || true
PID=
if java -jar "$JAR" --spring.profiles.active=pi --mealplan.fixture-auth=true --mealplan.database-path="$WORK/rejected.sqlite" --mealplan.public-base-url=https://packaged.example.test --mealplan.content-path="$ROOT/content" > "$WORK/rejected.log" 2>&1; then
  echo 'Pi fixture startup must fail.' >&2
  exit 1
fi
grep -q 'Fixture authentication requires the local target' "$WORK/rejected.log"
printf 'Packaged Pi-profile boundaries, safe logs and fixture rejection passed on local hardware.\n'
