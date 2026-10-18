#!/usr/bin/env bash
set -euo pipefail
umask 077
RELEASE="${RELEASE_DIR:-/var/lib/mealplan/current}"
STATE="${STATE_DIR:-/var/lib/mealplan}"
KIND="${1:-daily}"
[[ "$KIND" == daily || "$KIND" == pre-deploy ]] || {
  echo 'Usage: backup.sh [daily|pre-deploy]' >&2
  exit 1
}
mkdir -p "$STATE/backups"
exec 8> "$STATE/backup.lock"
flock -x 8
SNAPSHOT="$STATE/backups/$KIND-$(date -u +%Y%m%dT%H%M%S)-$$"
java -jar "$RELEASE/app.jar" backup-database --database "$STATE/app.sqlite" --release "$RELEASE" --output "$SNAPSHOT"
# Verify a disposable restoration before a snapshot can become eligible for retention.
java -jar "$RELEASE/app.jar" restore-database --backup "$SNAPSHOT" --release "$RELEASE" --output "$SNAPSHOT/validated.sqlite"
rm "$SNAPSHOT/validated.sqlite"
if [[ "$KIND" == daily ]]; then
  # One retained snapshot per UTC day, seven daily dates; pre-deploy snapshots are retained separately.
  declare -A SEEN=()
  DAYS=0
  while IFS= read -r NAME; do
    DAY="${NAME:6:8}"
    if [[ -n "${SEEN[$DAY]:-}" || "$DAYS" -ge 7 ]]; then
      rm -rf -- "$STATE/backups/$NAME"
    else
      SEEN[$DAY]=1
      DAYS=$((DAYS + 1))
    fi
  done < <(find "$STATE/backups" -mindepth 1 -maxdepth 1 -type d -name 'daily-*' -printf '%f\n' | sort -r)
fi
printf '%s\n' "$SNAPSHOT"
