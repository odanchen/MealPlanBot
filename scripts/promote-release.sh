#!/usr/bin/env bash
# Internal transaction shared by deployment and disposable recovery tests.
# Caller supplies STATE, RELEASE, STAGE and SHA after artifact/preflight validation.
set -euo pipefail
: "${STATE:?}" "${RELEASE:?}" "${STAGE:?}" "${SHA:?}"
PREVIOUS=
if [[ -L "$STATE/current" ]]; then PREVIOUS="$(readlink -f "$STATE/current")"; fi
SNAPSHOT=
STOPPED=false
rollback() {
  local STATUS=$?
  trap - ERR
  if [[ "$STOPPED" == true ]]; then
    sudo systemctl stop mealplan-web.service
    if [[ -n "$SNAPSHOT" && -n "$PREVIOUS" ]]; then
      java -jar "$PREVIOUS/app.jar" restore-database --backup "$SNAPSHOT" --release "$PREVIOUS" --output "$STAGE/rollback.sqlite"
      rm -f "$STATE/app.sqlite-wal" "$STATE/app.sqlite-shm"
      mv "$STAGE/rollback.sqlite" "$STATE/app.sqlite"
    elif [[ -z "$PREVIOUS" ]]; then
      echo 'First deployment failed; service stays stopped for inspection.' >&2
      exit "$STATUS"
    fi
    ln -s "$PREVIOUS" "$STATE/current.rollback"
    mv -Tf "$STATE/current.rollback" "$STATE/current"
    printf 'MEALPLAN_COMMIT=%s\n' "$(jq -r .sourceSha "$PREVIOUS/manifest.json")" > "$STATE/runtime.env"
    sudo systemctl start mealplan-web.service
  fi
  echo 'Deployment failed; diagnostics remain in the system journal and staged release.' >&2
  exit "$STATUS"
}
trap rollback ERR
exec 8> "$STATE/backup.lock"
flock -x 8
sudo systemctl stop mealplan-web.service
STOPPED=true
if [[ -f "$STATE/app.sqlite" ]]; then
  [[ -n "$PREVIOUS" && -f "$PREVIOUS/manifest.json" ]] || {
    echo 'Existing database requires a matching prior release.' >&2
    false
  }
  CANDIDATE="$STATE/backups/pre-deploy-$(date -u +%Y%m%dT%H%M%S)-$$"
  mkdir -p "$STATE/backups"
  java -jar "$PREVIOUS/app.jar" backup-database --database "$STATE/app.sqlite" --release "$PREVIOUS" --output "$CANDIDATE"
  java -jar "$PREVIOUS/app.jar" restore-database --backup "$CANDIDATE" --release "$PREVIOUS" --output "$STAGE/verified.sqlite"
  SNAPSHOT="$CANDIDATE"
fi
java -jar "$RELEASE/app.jar" migrate-database --release "$RELEASE" --output "$STATE/app.sqlite"
ln -s "$RELEASE" "$STATE/current.next"
mv -Tf "$STATE/current.next" "$STATE/current"
printf 'MEALPLAN_COMMIT=%s\n' "$SHA" > "$STATE/runtime.env"
# Explicit one-runtime cutover; absence of the former unit is harmless.
if sudo systemctl cat mealplan-bot.service > /dev/null 2>&1; then sudo systemctl disable --now mealplan-bot.service; fi
sudo systemctl start mealplan-web.service
READY=false
for ATTEMPT in {1..60}; do
  if curl -fsS http://127.0.0.1:8000/health > "$STAGE/health.json" && jq -e --arg sha "$SHA" --arg revision "$(jq -r .contentRevision "$RELEASE/manifest.json")" '.status == "ready" and .commit == $sha and .revision == $revision' "$STAGE/health.json" > /dev/null; then
    READY=true
    break
  fi
  sleep 1
done
[[ "$READY" == true ]]
curl -fsS http://127.0.0.1:8000/ > "$STAGE/public.html"
grep -q '<div id="root"' "$STAGE/public.html"
STOPPED=false
trap - ERR
# Retain current and previous application releases. Backups retain their manifest identity.
for OLD in "$STATE/releases/"*; do
  [[ "$OLD" == "$RELEASE" || "$OLD" == "$PREVIOUS" ]] || rm -rf -- "$OLD"
done
printf 'Deployed %s\n' "$SHA"
