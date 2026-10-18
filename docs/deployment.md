# Deployment and recovery

The release is one executable Java 25 JAR, validated content, backup scripts and a complete
checksum inventory. The Pi needs Java, Bash, SQLite native-library support, coreutils,
util-linux (`flock`), curl, jq, unzip and GitHub CLI; it needs neither Gradle nor Node nor
the removed Python application. Recovery exercises also use the `sqlite3` and `zip` tools.

## One-time provisioning

Use the confirmed Debian arm64 Pi and a maintained Temurin Java 25 aarch64 runtime.
Install it using the vendor's official instructions and make `/usr/bin/java -version`
report 25. Verify `uname -m` reports `aarch64`. Do not substitute Debian 12's Java 17.
Keep `/opt/mealplan`, the unprivileged `mealplan` user/group, and the existing cloudflared
origin `http://127.0.0.1:8000`. No tunnel restart is normally required.

Provision `/var/lib/mealplan` owned by mealplan with mode 0700. Keep releases, current
symlink, SQLite/WAL, snapshots and runtime commit identity there. The deploy operator
must write that directory and have narrowly scoped sudo permission to stop/start
`mealplan-web.service`, inspect `mealplan-bot.service`, and disable/stop the old bot.
Install the reviewed units from `systemd/` into `/etc/systemd/system`, then run
`systemctl daemon-reload`. Enable the backup timer after the first successful cutover.
Never start the new web unit against an existing unidentified legacy database.

Create root-owned mode-0600 `/etc/mealplan/mealplan.env` from `.env.example`. Set the
canonical public HTTPS origin and Google client credentials; never commit them. Register
exactly `https://YOUR_HOST/login/oauth2/code/google` in the Google OAuth web client.
Only openid/email/profile scopes are requested. Any Google account can sign in;
list access still requires membership. Cancellation returns to the sign-in error view.
Sessions are HttpOnly/Secure/SameSite=Lax, time out after seven idle days, and end on
logout or application restart. The Pi unit explicitly rejects fixture authentication.

Configure read access to this repository's Actions artifacts with `gh auth login` or
`GH_TOKEN` in the operator's protected environment, outside Git. Private repositories
need appropriate repository Actions-read access. The application service does not need
artifact credentials. Do not place tokens in command arguments or the runtime bundle.

The web unit starts with `-Xms64m -Xmx256m`, StateDirectory, strict filesystem protection
and PrivateTmp. SQLite JDBC extracts into the private temporary directory; actual Pi
startup with these exact restrictions must still be exercised. Bound journald storage
in the host's journald configuration, for example a reviewed 50 MiB system journal cap.
The 400 MiB RSS budget remains an unmeasured target.

## Routine operator delivery

```sh
cd /opt/mealplan
git pull --ff-only
./scripts/deploy-pi.sh --commit HEAD
```

The script requires a clean checkout and the requested commit at HEAD and on
`origin/main`. It selects a successful push build of `ci.yml` on main for that exact
full SHA. It downloads `mealplan-SHA` and verifies checksums, manifest, content, Java
and schema metadata before stopping the service. Missing/expired artifacts require a
fresh trusted build for that commit; the running release stays intact.

A deployment lock prevents concurrent operators. At least 512 MiB free space is
required; size actual snapshots/releases before proceeding on a nearly full card.
A separate backup lock prevents a daily snapshot during migration. The transaction
stops the web service, takes and test-restores a matching pre-deploy snapshot, runs
atomic migrations, switches the current symlink, and starts the web service. It
checks expected SHA/content revision at `/health` and the public `/` shell. The old
bot is disabled and stopped if its installed unit exists. Confirm its status on the
actual target and record that only the Java web runtime remains.

On migration/health failure, the same transaction stops writes and restores the prior
application/content link and matching database. It never runs an old binary against a
new incompatible schema. A first-deployment failure leaves the service stopped for
inspection. Inspect `journalctl -u mealplan-web.service`, preserve diagnostics, and
remove a failed staged SHA directory only after review before retrying. A matching
prior release is mandatory when a database already exists. The script retains current
and previous application releases. No automatic production deployment runs on push.

## Backup and disposable restore

The daily timer creates a VACUUM INTO snapshot that includes committed WAL pages.
Snapshots include the exact release manifest and database/manifest checksums. Each
snapshot is restored to a disposable file and checked for SQLite integrity, foreign
keys, schema version and applied migration checksums before retention cleanup. Keep
one snapshot per UTC day for seven daily dates; pre-deploy snapshots are retained
separately for operator pruning and never silently aged out by daily retention.

```sh
sudo -u mealplan /bin/bash /var/lib/mealplan/current/scripts/backup.sh daily
./scripts/restore.sh /var/lib/mealplan/backups/SNAPSHOT /var/lib/mealplan/releases/MATCHING_SHA /var/lib/mealplan/disposable.sqlite
```

Restore output must not exist. These commands do not replace the live database. For
manual promotion, stop the web service, validate into a disposable file, remove stale
live WAL/SHM files, replace the database and activate its matching release, then start
and verify health. Retain/retrieve the exact application/content bundle needed by older
snapshots; a manifest alone cannot run an old release. Store that bundle off-device
alongside important backups. Same-card snapshots do not protect against card loss;
off-device backup needs a separately configured destination and a real restore exercise.

## Release verification and remaining acceptance

```sh
./scripts/check.sh
./scripts/build.sh # clean checkout only
./scripts/test-release-integrity.sh --clean-build
./scripts/test-release-recovery.sh
./scripts/test-packaged-boundaries.sh
```

The integrity script builds an isolated clean snapshot of current source without
committing the user's working tree. It checks unchanged dependency metadata, repeated
bundle bytes and rejected tampering/dirty/plain-JAR cases. Recovery runs real packaged
Java/SQLite with controlled service/health adapters; it is not proof of Pi systemd or
Google behavior. On macOS only, its development adapters supply flock semantics and
BSD atomic rename support. Linux uses its real util-linux and coreutils commands.

Run `scripts/measure-household-load.sh LIST_ID COOKIE_DIRECTORY NEW_OUTPUT_DIRECTORY`
on the actual Pi with ten member sessions (`*.cookies`) and at least 500 active entries.
Use disposable acceptance accounts/lists. Output contains timings/resource counters;
protect cookie files and delete them afterwards. Also record cold readiness startup,
idle/peak RSS/CPU, long-run SD growth/contention and visible/hidden browser behavior;
HTTP timings alone cannot prove five-second UI visibility. Run real two-account Google
sign-in/cancel/logout/invitation checks through the tunnel, and the ten-person timed
unassisted study. Record results in `docs/validation/`; leave their tasks open until measured.
