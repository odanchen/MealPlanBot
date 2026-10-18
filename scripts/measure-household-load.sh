#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ "$(uname -m)" == aarch64 ]] || {
  echo 'Measurements must run on the actual arm64 Pi.' >&2
  exit 1
}
[[ $# == 3 ]] || {
  echo 'Usage: measure-household-load.sh LIST_ID COOKIE_DIRECTORY OUTPUT_DIRECTORY' >&2
  exit 1
}
LIST="$1" COOKIE_DIR="$2" OUTPUT="$3"
[[ "$LIST" =~ ^[0-9a-f-]{36}$ && ! -e "$OUTPUT" ]]
BASE="${BASE_URL:-http://127.0.0.1:8000}"
mkdir -m 700 "$OUTPUT"
PID="$(systemctl show mealplan-web.service --property MainPID --value)"
[[ "$PID" -gt 0 ]]
mapfile -t COOKIES < <(find "$COOKIE_DIR" -maxdepth 1 -type f -name '*.cookies' | sort)
[[ "${#COOKIES[@]}" == 10 ]] || {
  echo 'Supply ten authenticated member cookie files named *.cookies.' >&2
  exit 1
}
ps -p "$PID" -o pid=,rss=,%cpu=,etime= > "$OUTPUT/before.txt"
cat "/proc/$PID/io" > "$OUTPUT/io-before.txt"
du -k /var/lib/mealplan/app.sqlite* > "$OUTPUT/disk-before.txt"
PIDS=()
for INDEX in {0..9}; do
  (
    COOKIE="${COOKIES[$INDEX]}"
    CSRF="$(curl -fsS -b "$COOKIE" "$BASE/api/csrf")"
    HEADER="$(jq -r .headerName <<< "$CSRF")"
    TOKEN="$(jq -r .token <<< "$CSRF")"
    DATA="$(curl -fsS -b "$COOKIE" "$BASE/api/lists/$LIST/entries")"
    [[ "$(jq '.entries | length' <<< "$DATA")" -ge 500 ]]
    ENTRY="$(jq -r --argjson index "$INDEX" '.entries | sort_by(.id) | .[$index].id' <<< "$DATA")"
    for ROUND in {1..20}; do
      curl -fsS -b "$COOKIE" -w '%{time_total}\n' -o "$OUTPUT/private-$INDEX.json" "$BASE/api/lists/$LIST/entries" >> "$OUTPUT/read-$INDEX.seconds"
      VERSION="$(jq -r --arg id "$ENTRY" '.entries[] | select(.id==$id) | .version' "$OUTPUT/private-$INDEX.json")"
      curl -fsS -b "$COOKIE" -X PATCH -H "$HEADER: $TOKEN" -H 'Content-Type: application/json' -H "If-Match: \"$VERSION\"" -d '{"bought":true}' -w '%{time_total}\n' -o /dev/null "$BASE/api/lists/$LIST/entries/$ENTRY" >> "$OUTPUT/write-$INDEX.seconds"
    done
    rm "$OUTPUT/private-$INDEX.json"
  ) &
  PIDS+=("$!")
done
# Capture process and SQLite growth during the actual concurrent workload.
while kill -0 "${PIDS[0]}" 2> /dev/null; do
  ps -p "$PID" -o rss=,%cpu= >> "$OUTPUT/resources.txt"
  sleep 0.2
done
for JOB in "${PIDS[@]}"; do wait "$JOB"; done
cat "/proc/$PID/io" > "$OUTPUT/io-after.txt"
du -k /var/lib/mealplan/app.sqlite* > "$OUTPUT/disk-after.txt"
cat "$OUTPUT"/read-*.seconds "$OUTPUT"/write-*.seconds | sort -n > "$OUTPUT/latencies.seconds"
awk '{v[NR]=$1} END {i=int(NR*.95+.999); printf "operations=%d p95_seconds=%.6f\\n",NR,v[i]; if(v[i]>2)exit 1}' "$OUTPUT/latencies.seconds" | tee "$OUTPUT/summary.txt"
awk '{if($1>peak)peak=$1} END {printf "peak_rss_mib=%.2f\\n",peak/1024}' "$OUTPUT/resources.txt" | tee -a "$OUTPUT/summary.txt"
echo 'Record cold readiness startup separately with service timestamps and /health polling. Run browser shared-shopping visibility/hidden-view tests against this target; HTTP timing alone does not establish five-second UI visibility.' | tee -a "$OUTPUT/summary.txt"
