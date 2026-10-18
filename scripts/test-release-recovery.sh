#!/usr/bin/env bash
set -euo pipefail
# Runs real packaged Java/SQLite migrations and restoration in disposable state.
# Production service/tunnel/hardware acceptance is recorded separately.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
WORK="$(cd "$WORK" && pwd -P)"
trap 'rm -rf -- "$WORK"' EXIT
JAR="$ROOT/backend/build/libs/mealplan-0.1.0.jar"
SHA="$(git -C "$ROOT" rev-parse HEAD)"
java -jar "$JAR" package-release --root "$ROOT" --jar "$JAR" --content "$ROOT/content" --commit "$SHA" --dirty false --output "$WORK/release.zip"
mkdir "$WORK/release"
unzip -q "$WORK/release.zip" -d "$WORK/release"
java -jar "$JAR" migrate-database --release "$WORK/release" --output "$WORK/app.sqlite"
# Seed private data including invalidated invitation and a completed, detached source.
sqlite3 "$WORK/app.sqlite" << 'SQL'
PRAGMA foreign_keys=ON;
INSERT INTO users VALUES('00000000-0000-4000-8000-000000000001','Owner',NULL,1,1);
INSERT INTO shopping_lists(id,name,owner_user_id,created_at) VALUES('list','Household','00000000-0000-4000-8000-000000000001',1);
INSERT INTO memberships VALUES('list','00000000-0000-4000-8000-000000000001',1);
INSERT INTO invitations VALUES('invite','list','hash','00000000-0000-4000-8000-000000000001',1,259200001,2);
INSERT INTO schedule_attachments(id,list_id,detached_at,enabled,interval_unit,interval_count,weekday,local_time,time_zone,recurrence_start_date,next_due_at,created_at) VALUES('attachment','list',2,0,'week',1,6,'09:00','UTC','2026-10-20',1,1);
INSERT INTO schedule_occurrences(id,attachment_id,kind,scheduled_at,configuration_version,coverage_start_date,coverage_end_date_exclusive,time_zone,status,completed_at) VALUES('occurrence','attachment','initial',1,0,'2026-10-20','2026-10-24','UTC','completed',2);
INSERT INTO shopping_entries(id,list_id,display_name,created_at,bought_at,archived_at,occurrence_id) VALUES('entry','list','Rice',1,2,3,'occurrence');
SQL
sqlite3 "$WORK/app.sqlite" .dump > "$WORK/original.sql"
java -jar "$JAR" backup-database --database "$WORK/app.sqlite" --release "$WORK/release" --output "$WORK/snapshot"
# Inject an actual failed migration into a new executable JAR and package it normally.
cp "$JAR" "$WORK/broken.jar"
mkdir -p "$WORK/patch/BOOT-INF/classes/db/migrations"
printf 'CREATE TABLE should_roll_back(id TEXT); INVALID MIGRATION;\n' > "$WORK/patch/BOOT-INF/classes/db/migrations/V006__failure.sql"
(cd "$WORK/patch" && zip -q "$WORK/broken.jar" BOOT-INF/classes/db/migrations/V006__failure.sql)
java -jar "$JAR" package-release --root "$ROOT" --jar "$WORK/broken.jar" --content "$ROOT/content" --commit "$SHA" --dirty false --output "$WORK/broken.zip"
mkdir "$WORK/broken"
unzip -q "$WORK/broken.zip" -d "$WORK/broken"
if java -jar "$WORK/broken/app.jar" migrate-database --release "$WORK/broken" --output "$WORK/app.sqlite"; then
  echo 'Expected migration failure.' >&2
  exit 1
fi
sqlite3 "$WORK/app.sqlite" .dump > "$WORK/after-failure.sql"
cmp "$WORK/original.sql" "$WORK/after-failure.sql"
# Exercise the same stopped-service promotion/rollback code used by deploy-pi.sh.
mkdir "$WORK/bin"
cat > "$WORK/bin/sudo" << 'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$TEST_STATE/service-actions"
# The retired bot is absent from this fixture.
if [[ "$*" == 'systemctl cat mealplan-bot.service' ]]; then exit 1; fi
MOCK
cat > "$WORK/bin/curl" << 'MOCK'
#!/usr/bin/env bash
# Deliberately fail the candidate health probe; old service control is recorded.
exit 22
MOCK
printf '#!/usr/bin/env bash\nexit 0\n' > "$WORK/bin/sleep"
chmod +x "$WORK/bin/"*
# macOS supplies neither util-linux flock nor GNU mv -T. These adapters preserve
# file locking and no-follow atomic replacement while exercising the shared shell.
if [[ "$(uname -s)" == Darwin ]]; then
  cat > "$WORK/bin/flock" << 'MOCK'
#!/usr/bin/env python3
import fcntl, sys
fcntl.flock(int(sys.argv[-1]), fcntl.LOCK_EX)
MOCK
  cat > "$WORK/bin/mv" << 'MOCK'
#!/usr/bin/env bash
if [[ "$1" == -Tf ]]; then exec /bin/mv -h -f "$2" "$3"; else exec /bin/mv "$@"; fi
MOCK
  chmod +x "$WORK/bin/flock" "$WORK/bin/mv"
fi
for FAILURE in migration health; do
  STATE="$WORK/state-$FAILURE"
  mkdir -p "$STATE/releases" "$WORK/stage-$FAILURE"
  cp -R "$WORK/release" "$STATE/releases/previous"
  if [[ "$FAILURE" == migration ]]; then cp -R "$WORK/broken" "$STATE/releases/candidate"; else cp -R "$WORK/release" "$STATE/releases/candidate"; fi
  cp "$WORK/app.sqlite" "$STATE/app.sqlite"
  ln -s "$STATE/releases/previous" "$STATE/current"
  if PATH="$WORK/bin:$PATH" TEST_STATE="$STATE" bash -c 'STATE="$1"; RELEASE="$STATE/releases/candidate"; STAGE="$2"; SHA="$3"; source "$4/scripts/promote-release.sh"' _ "$STATE" "$WORK/stage-$FAILURE" "$SHA" "$ROOT"; then
    echo 'Expected deployment failure.' >&2
    exit 1
  fi
  [[ "$(readlink "$STATE/current")" == "$STATE/releases/previous" ]]
  sqlite3 "$STATE/app.sqlite" .dump > "$WORK/restored.sql"
  cmp "$WORK/original.sql" "$WORK/restored.sql"
  grep -q 'systemctl start mealplan-web.service' "$STATE/service-actions"
  [[ "$(sqlite3 "$STATE/app.sqlite" 'PRAGMA integrity_check')" == ok ]]
  [[ -z "$(sqlite3 "$STATE/app.sqlite" 'PRAGMA foreign_key_check')" ]]
done
# Migrations remain idempotent and cannot reactivate a detached attachment.
java -jar "$JAR" migrate-database --release "$WORK/release" --output "$WORK/app.sqlite"
[[ "$(sqlite3 "$WORK/app.sqlite" "SELECT count(*) FROM schedule_attachments WHERE source_schedule_id IS NULL AND enabled=0")" == 1 ]]
if sqlite3 "$WORK/app.sqlite" "INSERT INTO schedule_occurrences SELECT 'duplicate',attachment_id,kind,scheduled_at,configuration_version,coverage_start_date,coverage_end_date_exclusive,time_zone,content_revision,source_schedule_version,effective_template_id,effective_anchor_date,status,failure_code,completed_at FROM schedule_occurrences"; then
  echo 'Deduplication failed.' >&2
  exit 1
fi
printf 'Isolated migration failure and matched-release restoration passed. Production service rollback still requires target verification.\n'
