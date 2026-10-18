# Implementation Plan: Upgrade Recipe Hosting and Shared Meal Shopping

**Branch**: `001-upgrade-recipe-planner` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification and Java/React, Google-only authentication, Raspberry Pi/Cloudflare Tunnel, and local-execution requirements. Build-tool amendment: use Gradle instead of Maven.

**Status**: Phase 0/1 design complete and amended after the schedule-model review. Public/private journeys, live links, any-date anchors, initial batches, pre-anchor dates, and source deletion are resolved. Java 25 with Boot 4.1 is the selected baseline after confirmation of arm64; the earlier Java 17 alternative is compared in research.md. Setup reported feature identifier `001-upgrade-recipe-planner` as BRANCH; implementation now runs on the `001-upgrade-recipe-planner` feature branch.

## Summary

Replace the coupled Python runtime and duplicated recipe HTML with a Java backend and React frontend. Keep recipes as authoritative structured content, render them with shared components, and persist household collaboration state transactionally. Preserve scheduling, per-item age-based archiving, private sharing, and five-second automatic shared-list updates; remove Telegram. Provide an isolated local execution mode and deploy primarily on the existing Raspberry Pi behind Cloudflare Tunnel.

The proposed shape is one Java application serving a built React frontend and same-origin endpoints, with business rules independent of HTTP, persistence, and scheduling adapters. Google OpenID Connect is the only production sign-in mechanism; the local profile uses one stable stub test user and requires no Google connection or credentials. First successful sign-in provisions an immutable application UUID mapped to the validated Google issuer and subject; email is mutable profile data. No application passwords, password reset, or Google API authorization are needed.

## Technical Context

**Language/Version**: Java 25 bytecode/runtime, React 19.x and TypeScript frontend (interpreting “Reach” as React). Pin supported patched dependency versions and Node/build tooling during implementation.

**Primary Dependencies**: Spring Boot 4.1.x with its managed Spring Security OAuth2/OIDC client; JDBC with Xerial SQLite driver; React, TypeScript, and Vite. Use ordinary JDBC rather than adding Hibernate/JPA. Use backend-owned sessions rather than storing Google tokens in browser storage. Confirm exact versions during design finalization.

**Build Tool**: Gradle 9.x (at least 9.1.0 for running on Java 25), Kotlin DSL, and a committed backend-local Gradle Wrapper. Pin an exact supported patch and distribution SHA-256 during implementation. Use `backend/settings.gradle.kts`, `backend/build.gradle.kts`, `backend/gradlew`, `backend/gradlew.bat`, and `backend/gradle/wrapper/gradle-wrapper.{jar,properties}`. Configure a Java 25 toolchain and release level 25, Spring Boot dependency management, dependency locking, and reviewed dependency verification metadata. Local scripts and CI invoke the wrapper; no system Gradle installation is required after bootstrap. Frontend dependencies remain managed with npm and its lockfile.

**Storage**: SQLite selected by the user for mutable state locally, in integration tests, and on the Pi. PostgreSQL and persistent H2 are rejected alternatives documented in research.md. Recipes remain authoritative repository files with optional local image assets.

**Testing**: Domain tests with controlled time; actual-engine persistence/concurrency tests; HTTP authorization and Google claim-mapping tests without external calls; frontend component/accessibility checks and browser end-to-end tests; explicit real-Google sign-in smoke test.

**Target Platform**: Raspberry Pi 3 Model B Plus Rev 1.3, Debian 12 bookworm, approximately 907 MiB visible RAM (657 MiB available in the supplied snapshot), 199 MiB swap, SD-card root filesystem about 15 GiB with 8 GiB free. Existing cloudflared forwards to http://127.0.0.1:8000. Preserve that origin and adapt systemd/mealplan-web.service. OS architecture is confirmed arm64; deployment preflight verifies that and the selected maintained Java 25 runtime. Developer-machine local mode uses isolated SQLite files.

**Project Type**: Web application with operator validation and preview commands.

**Performance Goals**: Ten simultaneous members, 500 active items/list; acting-user reads/actions p95 at most two seconds; saved changes reach connected visible views within five seconds.

**Constraints**: Preserve recipe content and requested read-only/validation/preview capabilities through new interfaces; no Telegram, no local password system; private-list authorization on every operation; transactional schedule occurrence deduplication; independent item expiry. Local mode is explicitly selected to use a single stable stub identity. Production must reject the stub configuration and bind only to loopback behind the tunnel. No local multi-user login selector.

**Scale/Scope**: Fifteen migrated tutorials, one 21-day recipe schedule, method recipe references, curated terms/ingredient aliases/categories, multiple private household lists. Public recipe editing and user-authored schedule templates excluded; user signup for an available template with a personal anchor is included.

## Constitution Check

Initial gate review: PASS. User selections are SQLite everywhere, one local stub identity, GitHub Actions builds, single-file YAML recipes with images, and Google sign-in open to all Google users.

- Pragmatic simplicity: one backend application; static frontend can run within its origin. SQLite is selected; no separate database service is needed. No microservices, Redis, or message broker justified by this scale.
- Separate business logic: schedule coverage, ingredient arithmetic, membership rules, and item expiry belong to independently testable domain services.
- Authoritative data and integrity: file content is recipe truth; transactional state holds users and collaboration. Recipe projections are read-only and validated. Materialized purchases retain historical quantities.
- Usability/accessibility: shared frontend components, keyboard/touch term lookup, clear disconnected states, and predictable list ordering.
- Meaningful verification/simple operations: test privacy, identity stability, concurrency, exact quantities, recurrence boundaries, and per-item expiry; choose local/Pi parity and restore validation over speculative scale.

Post-design gate review: PASS. data-model.md, contracts/content-format.md, contracts/http-api.md, and quickstart.md preserve the principles with one runtime, explicit transactional boundaries, shared accessible components, and meaningful Pi/privacy/recovery checks. No unjustified violations identified. Analysis remediation is applied for visibility-only refresh, covered-period bookkeeping, one-month query limits, MVP routing, populated migration verification, and endpoint wording. A2 is resolved: personal and shared-list dated views use each requesting viewer’s browser/device time zone for today and query bounds; actual additions retain the attachment’s configured zone. The approved analysis findings are addressed. Schedule access requires authenticated personal configurations. Live-link imports, any-date cycle starts, immediate first batches, not-started dates, and source deletion/detachment now have explicit requirements, transitions, and acceptance coverage. Actual Pi performance and production OAuth remain implementation/deployment acceptance gates, not planning claims.

Build-tool amendment gate review: PASS before and after design. One Gradle backend project retains the existing domain boundaries and deployment shape. Wrapper/version/checksum pinning and locked, verified dependencies support repeatable local/CI builds. Data model and public/operator contracts were reviewed and need no changes for this amendment; the Pi still runs the packaged JAR without build tooling.

## Project Structure

### Documentation (this feature)

```text
specs/001-upgrade-recipe-planner/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/          # Content/glossary and HTTP/auth/operator contracts
└── checklists/requirements.md
```

### Source Code (repository root)

Selected source boundaries; these directories are implementation targets, not evidence that the rewrite is already present.

```text
backend/                # Java application, domain rules, persistence and web adapters
frontend/               # React pages, shared components, typed client
content/                # Authoritative recipe, ingredient, term, schedule content/assets
systemd/                # Existing Pi service adapted to Java
scripts/                # Local build/run, content validation, backup/restore tooling
```

**Structure Decision**: Keep backend, frontend, and content distinct while packaging the built frontend with the backend for Pi and local production-preview operation. Do not retain the Python app as a second authoritative runtime after cutover. Native JAR/systemd deployment is selected to fit the existing daemon and constrained Pi; no containers are required. GitHub Actions builds/tests the commit-keyed deployment bundle; the Pi retrieves it through an operator-invoked deploy command. The deployment procedure must preserve the external SQLite state directory and stop the old Telegram service at cutover.

## Complexity Tracking

No constitution violations identified. SQLite, one Java runtime process, and the existing tunnel/systemd arrangement minimize operating overhead. Do not run Node, a separate scheduler service, or the former Telegram bot in production.

## Deployment Design

Keep the existing unprivileged `mealplan` account, `/opt/mealplan` checkout, `/etc/mealplan/mealplan.env`, origin port 8000, and `mealplan-web.service` identity. Replace the Python ExecStart with the packaged Java application. The existing ProtectSystem=strict policy must gain a narrowly scoped writable state location, proposed `/var/lib/mealplan`, through StateDirectory=mealplan; keep SQLite, WAL, and backup files out of the Git checkout. Remove the Python-specific environment setting and disable the old bot service during cutover.

Build React assets in GitHub Actions and bundle them into the Java artifact; include the validated YAML content/images in the versioned release bundle. Git pull alone updates sources, not the running JAR; the deployment script must acquire or build the matching tested artifact before switching, take a consistent backup, run migrations, restart the web service, and verify health. Preserve the previous artifact; rollback after a schema change must restore its matching backup rather than blindly run older code against a newer schema. Routine delivery is `git pull --ff-only` followed by one deploy command for that commit. Any Google user can create an account; private-list authorization remains membership-based. Recipe authoring uses one YAML file plus images in each recipe directory and shared catalogue files.

Initial resource budget to validate on the Pi: Java heap cap around 256 MiB and process RSS target below 400 MiB under the stated household workload. These are engineering targets, not measured results. Set a modest connection/thread budget, avoid ORM and framework extras without a need, and measure startup, RSS, SD writes, and repeated updates on the actual board. Do not rely on swap to meet acceptance criteria.

## Application Design

Use one Gradle backend project with domain packages for recipes/measurements, rotation/recurrence, identities/memberships, and shopping/archive state. HTTP, SQLite/JDBC, file parsing, clock, and scheduler adapters call domain services; frontend calculations do not become a competing source for purchasing totals. Use Java BigDecimal and explicit conversion tables, with canonical decimal strings in SQLite. Version schema migrations with the application; keep transactions short and external/provider calls outside them.

The backend wrapper lives in `backend/`; from the repository root use `./backend/gradlew -p backend check` for backend verification and `./backend/gradlew -p backend bootJar` for the executable JAR under `backend/build/libs/`. Wire every backend test suite into `check`. `scripts/check.sh` also runs content/frontend checks. `scripts/build.sh` builds the locked frontend first, stages its output as generated backend resources, and invokes `bootJar`; declare the generated-resource task dependency so packaging cannot omit the React assets. The release script selects the executable Boot JAR explicitly. CI uses these same wrapper/script paths, checks wrapper integrity, and preserves dependency lock/verification files; the Pi downloads the tested bundle and needs neither Gradle nor Node. Use reproducible archive ordering and omit archive entry timestamps.

React/TypeScript uses shared layout/navigation, RecipePage, IngredientGroup, InlineContent/CookingTerm, ShoppingList, ItemRow, ArchiveView, and owner-settings components. Recipe instructions consist of explicit text/term/recipe segments; glossary defaults exist once in a shared file; optional per-occurrence explanations override them, and standalone label/explanation annotations need no glossary reference. See the content contract for the exact authoring example, validation, and accessible hover/click/focus behavior. Raw HTML and automatic word replacement are excluded.

Use two-second bounded polling for connected visible list views to meet five-second shared changes at household scale. Apply explicit set-bought operations with version preconditions; reject stale writes rather than overwriting silently. Stop polling invisible views, fetch fresh state immediately when they become visible, reload on reconnection, mark disconnected views, and do not queue offline writes. No separate realtime server is needed.

The backend scheduler processes due additions with unique occurrence keys, complete snapshot validation, and atomic batch completion. Candidate coverage runs from scheduled local date inclusive to next scheduled local date exclusive. Each attachment records its last successfully covered period; for an unchanged anchor, exclude already covered dates even after recurrence/time-zone edits. Commit coverage advancement with entries and occurrence completion, never on failure. Anchor-date changes clear all linked markers atomically without removing old checklist items or resetting occurrence retry identities. A separate small sweep may materialize individually expired items, while reads always enforce per-item age; there is no common weekly archive operation. Policy changes first materialize already-due items so extending the age limit cannot restore them. List deletion and invitation acceptance/generation serialize through transactional rechecks.

Google uses backend authorization-code OIDC, validated issuer/subject mapped to an internal UUID, application cookies, and CSRF protection. Provider tokens stay off browser storage. Any Google user may sign in, but only authenticated list members can read/mutate a list. In-memory sessions have a seven-day inactivity timeout and end on restart. Local mode uses one stable seeded user against isolated SQLite and cannot activate on the Pi deployment target.

## Migration and Verification Order

1. Capture recipe content and rotation expectations as comparison fixtures, without preserving previous transport payloads or command syntax; define YAML schemas and migrate all fifteen tutorials without changing amounts, serving estimates, or preparation guidance.
2. Build domain logic and content/operator validation with complete term/recipe/ingredient references. Keep the current app available until the new release passes comparison checks; retire Python runtime and Telegram at cutover.
3. Implement identity mapping and transactional list/membership/invitation state, followed by manual shopping, age-based archive, recurrence generation, and deletion/concurrency checks.
4. Render through shared frontend components and verify keyboard/touch terms, meaningful ingredient conversion, privacy, grouping/sorting, and five-second collaboration.
5. Produce locked CI builds, local stub startup, and commit-matched deployment/backup/restore commands. Run production Google smoke tests and actual-board resource/latency checks before declaring the rewrite deployable.

This is phase ordering, not tasks.md. `$speckit-tasks` generates the actionable implementation list after design review.

The build-tool task refresh is complete in `tasks.md`: T001, T005, T006, T091, T092 and T096 cover the wrapper/Kotlin DSL bootstrap, lock/verification files, `.gradle/` and `backend/build/` ignores, wrapper-based checks/build scripts, generated frontend resources, executable-JAR packaging, CI and development documentation. Accepted application/schedule requirements, task IDs, dependencies and completion state are preserved. Run `$speckit-analyze` next to check consistency, then `$speckit-implement` to execute the refreshed backlog. See quickstart.md for the command sequence and optional requirements-quality checklist.

## Design Outputs and Limits

- research.md: accepted choices, SQLite alternatives, version comparison, CI delivery and operational defaults.
- data-model.md: content versus transactional state, identity keys, lifecycle transitions, deduplication and concurrency rules.
- contracts/content-format.md: recipe/glossary YAML and shared rendering contract.
- contracts/http-api.md: public recipe/ingredient/glossary endpoints, authenticated personal/list schedule endpoints, private collaboration/authentication, refresh and operator interfaces.
- quickstart.md: target local/CI/Pi commands and end-to-end verification scenarios.

No application code, CI workflow, Google configuration, or Pi service has been changed or deployed by this planning phase. The user confirmed arm64. Use a maintained Linux aarch64 Java 25 distribution (Temurin), provisioning it separately from Debian 12’s default Java 17, and align CI/local/runtime at Java 25. Verify runtime installation and memory on the actual Pi. Daily/pre-deploy same-card backups are a starting point; off-device backup requires an operator-supplied destination and cannot be presumed configured.

## Meal Schedule Review

Recipes remain public. Schedule template discovery, signup, personal configuration, and dated meals require authentication. A user selects the available template and an anchor date to create a personal configured schedule independently of any shopping list. The sole supplied template describes the 21-day recipe/rest cycle without a global calendar anchor. It is data-driven, and additional template authoring is deferred.

A new list has no schedule. Its owner can import a personal configured schedule, with effective template and anchor, then configure list-specific ingredient additions (default weekly Saturday 09:00 America/Toronto). Members can view their list’s effective schedule but not another user’s private schedule catalogue. Import is confirmed as a live link: resolve template/anchor from the personal schedule for future runs, preserve existing entries, and keep addition settings per list. Any weekday may anchor cycle day 1, shifting all meal/rest slots together; the ingredient-addition weekday is independent. Import creates an immediate initial batch for dates before the next regular addition date, then follows the unchanged recurrence. Both initial and recurring batches are idempotent. Dates before the anchor show not_started and contribute nothing to initial or recurring batches; empty batches complete without rescheduling. Deleting a personal configuration warns about linked lists, atomically detaches all of them and stops future additions, and preserves purchases and source history. Generation rechecks source/link state before commit so it cannot restore deleted configuration or add items after deletion wins the race. Dated views are personal-configuration-specific, never global endpoints. Personal and shared-list dated views use the current viewer’s local zone, not the owner’s. Anchor/meal dates stay date-only and next-run instants may be formatted locally without changing actual generation. User-facing date queries are bounded to one calendar month per request and no later than one calendar month ahead of that viewer’s local today; partial previews are labeled and do not limit internal generation. An attachment’s covered-period marker survives addition-setting edits and item actions, and resets on an actual personal-anchor change.
