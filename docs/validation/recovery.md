# Release and recovery verification

Validated locally on 2026-10-18 with Java 25 and the actual SQLite JDBC engine.
All state was disposable. No production service or database was changed.

`scripts/test-release-integrity.sh --clean-build` copies the current tracked/untracked
source into an isolated repository and commits only that disposable snapshot. The
locked frontend/Gradle build succeeded; dependency locks and verification metadata
were unchanged and that checkout stayed clean. Two bundles made from the same executable
JAR were byte-identical. Real package/verify commands accepted the valid bundle and
rejected tampered payload, manifest, checksum inventory, missing/extra files, a plain
JAR and a release marked dirty. This proves the current implementation snapshot;
it does not label uncommitted workspace changes as the repository's original HEAD.

`ReleaseManifestTest` checks inventory integrity and WAL-consistent backup/restore
while the live SQLite pool is open. Restored state retains memberships, bought/archived
entries, withdrawn invitations, completed occurrence identities and detached sources.
Foreign-key and unique occurrence constraints remain enforced. Corrupt backup payloads
are rejected before restoration. Applied migration checksums bind state to the release.

`scripts/test-release-recovery.sh` runs real executable-JAR migrations, injects an
invalid V006 migration, and confirms transactional rollback preserves the original
populated database. It then invokes the same `promote-release.sh` transaction used by
deployment for separate migration-failure and health-failure cases. Service control
and network health responses are controlled adapters; SQLite, backup validation,
migration, file replacement and rollback are real. Both failures restore the prior
application/content symlink and a database whose SQL dump exactly matches the original.
Restart of the prior service is recorded, integrity/foreign-key checks pass, migrations
remain idempotent, withdrawn invitations stay withdrawn, detached sources stay disabled
and duplicate initial occurrences are still rejected.

On macOS, development adapters supply file locking and BSD no-follow atomic rename;
Linux uses util-linux/coreutils. CI runs these release checks on Ubuntu. The workflow
has been created locally; its hosted execution and artifact download are not claimed
until that source is pushed and a trusted build exists.

Actual systemd stop/start, arm64/JDBC PrivateTmp behavior, SD-card recovery and live
Google sessions still require the target. Daily retention code preserves seven UTC
daily dates plus separately retained pre-deploy snapshots; the target timer and
operator-configured off-device backup still require installation and exercise.
