# Quickstart and Validation Guide

These are the target commands to implement, not commands currently available in this Python repository. No deployment, Google configuration, or production service change has been performed during planning.

## Prerequisites

Local/CI build: selected JDK baseline 25, committed Gradle Wrapper in `backend/` (Gradle 9.x, at least 9.1.0, with an exact version and distribution SHA-256 pinned during implementation), a pinned Node version compatible with Vite (at least Node 22.12 for the chosen tooling), npm lockfile, Git. Backend build configuration uses Kotlin DSL, committed dependency locks and reviewed verification metadata. A global Gradle installation is unnecessary when using the wrapper. Runtime: matching Java runtime and local writable storage; SQLite is embedded. No Docker or separate database is required. Production uses Google OAuth credentials and the existing cloudflared tunnel. Local authentication uses one stub test user and needs neither credentials nor internet; initial dependency installation still requires network access or cached dependencies.

## Build, validate, run locally

```sh
./scripts/check.sh
./scripts/build.sh
./scripts/run-local.sh
```

`check.sh` runs content validation, backend domain/persistence/security tests through `./backend/gradlew -p backend check`, and frontend checks. All backend test suites must be wired into `check`. `build.sh` performs a reproducible locked frontend build, stages its output as generated backend resources, invokes `./backend/gradlew -p backend bootJar`, and emits a deployment bundle with the executable JAR from `backend/build/libs/`, content, manifest, and checksums. Verify the Boot JAR contains the built frontend and excludes credentials/state; do not select a plain JAR accidentally. `run-local.sh` runs the built application with the explicit local profile on http://127.0.0.1:8000 and creates ignored `.local/mealplan/app.sqlite`. One stable fixture user is authenticated without Google. Production state is never copied into local mode. Frontend hot reload is optional via a proxy and must preserve the same server-side authorization and CSRF behavior.

Once the backend exists, run these from the repository root to verify its build separately:

```sh
./backend/gradlew -p backend --version
./backend/gradlew -p backend check
./scripts/build.sh
```

Expect the pinned Gradle version on Java 25, passing backend checks, and a release bundle containing an executable Boot JAR with React assets. CI runs the same checks/build using the committed wrapper and validates its integrity. A clean checkout must build using committed locks/verification metadata without rewriting them. Ignore `.gradle/` caches and `backend/build/`, but commit both wrapper launchers, its JAR/properties, Kotlin DSL files, dependency locks and verification metadata. The Pi runs the packaged JAR; Gradle and Node are not production prerequisites.

`bootJar` packages the application but does not run tests; release CI must pass `scripts/check.sh` before publishing the result of `scripts/build.sh`.

Check the recipe directory, all fifteen recipes, linked methods, and term bubbles. Sign in as the local fixture user, select the supplied template, choose an anchor, and view that configured schedule’s dated meals/rest days without needing a shopping list. Create a list and add two identical manual entries; both remain independent. Restart locally: user UUID and saved lists remain the same. An extra test-only multi-principal harness, not local UI, covers sharing and access denial.

## CI/CD and routine Pi deployment

GitHub Actions on pull requests runs checks without production secrets and produces no deployable production promotion. Trusted main-branch CI runs the full suite and publishes a commit-keyed bundle. Include source SHA, content revision, Java target, schema migration version, and SHA-256 checksums. Third-party workflow actions are pinned by SHA; workflow permissions are minimal.

One-time Pi setup adapts existing `mealplan-web.service` to Java, preserves user/group/working directory and environment-file location, configures StateDirectory=mealplan for SQLite under `/var/lib/mealplan`, and declares a Pi deployment target that rejects local stub authentication. Keep the existing tunnel origin `http://127.0.0.1:8000`. Verify the confirmed arm64 architecture, Java 25 runtime, and JDBC native-library loading with the final hardening. Disable `mealplan-bot.service` at cutover. Only the application service, not cloudflared, normally needs restart.

After a successful trusted-main CI build, the intended operator flow is:

```sh
git pull --ff-only
./scripts/deploy-pi.sh --commit HEAD
```

The deploy script resolves HEAD to a full SHA, acquires a deployment lock, checks clean checkout/target/runtime/disk space, downloads that exact successful trusted build, verifies manifest/checksums, and stages a versioned release. It stops the application before migrations, takes a consistent backup, migrates, switches the active release, and restarts the service. Only narrowly scoped privilege is needed for service control; credentials stay outside the repository. Artifact availability/authentication failures leave the existing release running. This is operator-triggered delivery after automated CI, not deployment of every push without an operator action.

The script verifies `/health` reports the expected SHA and valid content/database, then performs a basic public-page smoke test. On failure it preserves diagnostics and restores the prior release; if schema changed, stop writes and restore the matching pre-deploy database backup as part of rollback. Never run an older binary against an incompatible new schema. Keep two application releases plus seven daily local snapshots; bound journal/log growth on the SD card.

## Google production smoke test

Configure production OAuth client credentials and canonical public HTTPS URL outside Git. Register the exact `/login/oauth2/code/google` callback; request only openid/email/profile. Verify two Google accounts can each sign in, receive distinct internal UUIDs, and create private lists. Invitation acceptance must require explicit action after login. Logout invalidates access. A simulated changed-email claim for the same validated issuer/subject must retain UUID and memberships in automated tests; matching email with another subject must not link accounts. Reboot/restart requires sign-in again but keeps account/list data.

## Acceptance scenarios

1. **Content preservation**: compare all fifteen tutorials and 21 rotation dates against the source-content comparison baseline. Check recipe links, images/alt text, instruction order, servings, bean preparation, optional purchase treatment, and previous/next wraparound. Validate the new slug-based read-only endpoints and schedule-specific date-range operator previews. Verify YAML schemas and response shapes contain only the new recipe identity; all recipe links, schedule references, and purchase origins use it.
2. **Glossary**: annotate two recipes with the same term, including an inflected label; change the shared definition once and publish the accepted content revision. Both inheriting occurrences render the same updated explanation. An overridden occurrence retains its local explanation without changing the glossary or other uses; a standalone label/explanation works without any glossary ID. Test hover, tap, keyboard focus/Escape, pinned dismissal, and 320-pixel layout. Matching unannotated words stay plain; explicitly referenced missing IDs fail validation even with overrides. Empty overrides and standalone controls lacking either label or explanation fail validation.
3. **Exact quantities**: 1 kg plus 500 g becomes 1.5 kg within a generated batch; compatible plural aliases combine; distinct products and incompatible package/count/volume units stay distinct; conversions meet 1% display tolerance. Separate manual additions never merge.
4. **Privacy and sharing**: unauthenticated and nonmember access fails; invitation valid just before 72 hours and invalid at/after it; withdrawn/deleted-list invitations fail. Accepting twice yields one membership. Owner cannot leave, nonowner can leave, and only owner can delete.
5. **Collaboration**: two test principals retain simultaneous different-entry changes. Stale same-entry version yields a conflict rather than silent overwrite. Visible connected views reflect saved changes within five seconds. Hidden views send no polling requests and fetch immediately when made visible. Reconnection refreshes state. Retries with the same request ID do not create a second entry; a new key is a deliberate separate entry.
6. **Recurrence**: create a list and verify no schedule is attached and no automatic additions run. Create a personal configured schedule by selecting the supplied template and an anchor; import it into the list and check editable defaults of Saturday 09:00 America/Toronto; change the personal schedule’s anchor and verify linked future views/generation follow it while existing items and list addition settings stay unchanged; verify a Monday anchor gives five meals Monday–Friday and rest Saturday/Sunday, with the second week starting the next Monday; test a Saturday-to-Monday recurrence edit skips meal dates already covered, and ensure failed generation or checking/removing/archiving items does not reset the marker. An actual anchor change clears markers on all linked lists without altering old entries. Then test daily, weekly, and every-two-week addition intervals in controlled time. Recurring coverage is `[current scheduled local date, next scheduled local date)` and rest days add nothing. Verify Tuesday import creates one immediate Tuesday–Friday batch for Saturday recurrence and Saturday then creates the next complete interval. Retry import without creating duplicate attachment/items. An import on Saturday before 09:00 has an empty initial date range and leaves Saturday to the 09:00 run. Verify dates before a future anchor show not_started and contribute no ingredients; a spanning batch includes only meals on/after the anchor, while a wholly pre-anchor batch completes empty and preserves recurrence. Repeat occurrence processing, process failure/correction, and restart/catch-up; each successful due occurrence creates one whole batch.
7. **Per-item archive**: with seven-day age limit, items created two days apart expire two days apart. At exact threshold the older item is absent from active totals and present in archive. Checking does not reset age. Changing the age limit evaluates each original creation time and never restores already archived entries. Clear one/all archived items without changing active ones.
8. **Races**: delete a list while an invitation is accepted or a scheduled addition runs. No post-deletion operation grants membership, exposes data, or recreates the list. Also delete a personal configured schedule linked to multiple lists: show an affected-list warning, then verify every list is detached, additions stop, and all active/archived entries and origin remain. Race source deletion against an uncommitted batch; deletion winning must prevent that batch. Another user cannot delete the personal configuration. Polling clients drop access while server authorization is immediate.
9. **Production isolation**: production/Pi startup with fixture authentication fails; local startup works without Google configuration and cannot bind publicly. Google secrets, tokens, invite credentials, and private-list payloads do not enter normal logs.
10. **Pi measurements**: test ten simultaneous users with 500 active entries/list, two-second acting-user p95 and five-second shared visibility; record cold startup, steady/peak RSS, CPU, SQLite contention, and disk growth. Begin with -Xms64m -Xmx256m and target RSS ≤400 MiB; measure rather than claim compliance or rely on swap.
11. **Recovery**: first upgrade a populated V003/V004 database to V005 and check retained manual/archived/checked entries, memberships, and occurrence foreign-key enforcement; force a migration failure and verify rollback preserves the original data. Then restore a SQLite-consistent snapshot and matching content/application revision into a disposable local state directory; verify memberships, entries, invites, and completed-occurrence deduplication. Same-card daily backups do not protect against SD-card loss. External backup requires a separately configured destination and restore exercise.

12. **Query bounds**: test calendar-month end clamping, exact maximum future date, oversized ranges, and partial derived previews; totals describe only the displayed uncovered range while generation still uses its full configured interval. Compute today/horizon using the requesting viewer’s browser/device zone. Test two members whose current local dates differ: each defaults to their own today, while an explicit same-date query yields the same meal. Display next-run instants in viewer local time and verify the actual instant, batch coverage/quantities and coverage marker remain unchanged. Test invalid/missing timeZone rejection and the labeled UTC client fallback.

## Relevant contracts

[Content format and glossary](contracts/content-format.md), [HTTP/auth/operator contract](contracts/http-api.md), [data model](data-model.md), [research decisions](research.md).

## Refreshing the implementation checklist

The Gradle amendment updates planning artifacts only. `tasks.md` has been refreshed for the Gradle plan, preserving accepted requirements, task IDs, dependencies and completion state. Continue with these skills in Codex, in order (these are not shell commands):

```text
$speckit-analyze
$speckit-implement
```

Review/fix any material analysis findings before implementation. The refreshed infrastructure tasks are T001, T005, T006, T091, T092 and T096; IDs were preserved. To incorporate future plan changes, rerun `$speckit-tasks` with instructions to preserve accepted requirements and completion state, then analyze again. All existing tasks are currently unchecked. `$speckit-checklist` is optional if a separate requirements-quality review of build/reproducibility guidance is desired; it creates/appends checklist items under `checklists/` and does not regenerate implementation tasks. `$speckit-converge` assesses remaining code-versus-design gaps and appends missing work after implementation has progressed; it is not needed to refresh this unstarted backlog. No `$speckit-specify`, `$speckit-clarify`, or constitution update is required for this build-tool-only amendment.
