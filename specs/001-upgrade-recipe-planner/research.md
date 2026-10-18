# Phase 0 Research: Recipe Planner Rewrite

**Status**: Research and schedule-access/lifecycle review complete; selected decisions are reflected in specification, data model, contracts, and validation scenarios. User selections and engineering defaults below resolve the design. The confirmed arm64 target resolves runtime selection to Java 25 and Boot 4.1; Java 17 remains a documented rejected operational alternative. No measured Pi performance is claimed.

## Accepted direction

- Java backend and React frontend, with reusable page components rendering recipe content files/directories.
- Primary deployment on Raspberry Pi through Cloudflare Tunnel; isolated local run mode.
- Google-only production sign-in; local runs use a single stable stub test user. First successful production sign-in provisions an internal immutable user ID. Email is not an identifier for ownership or membership.
- Existing recipe, sharing, recurring additions, and per-item archive requirements apply. Telegram is removed.

## Mutable-state database — selected

**Decision**: The user selected SQLite for mutable state in all environments, including local mode and persistence tests. No PostgreSQL or H2 deployment or secondary test engine.

**Rationale**: One local application instance and short transactions fit SQLite's intended embedded/server-backed website uses. PostgreSQL is attractive if stronger concurrency and future expansion justify another service. Persistent embedded H2 is a Java-native alternative. Files are suitable recipe source material but do not replace transactions for collaboration.

| Option | Fit | Operational and correctness implications |
| --- | --- | --- |
| SQLite | Minimal services for a single Pi instance | One writer at a time; short transactions, WAL and bounded busy handling; local filesystem only. Enable foreign keys per connection. Use canonical decimal text or documented scaled integers plus Java BigDecimal, not floating-point quantities. Validate the JDBC driver's target ARM support. |
| PostgreSQL | More concurrent writes and room to expand | Separate database service, credentials, upgrades, backups; native UUID and exact numeric support; same engine locally and on Pi. |
| Persistent H2 | Embedded Java SQL without a separate service | Exact decimal and JDBC support. Use persistent storage in local mode. Compatibility modes do not make H2 an equivalent test replacement for another production engine. |
| Files only | Authoritative reviewed recipes and assets | Concurrent membership changes, invite revocation, item updates, and atomic occurrence completion would require rebuilding database behavior. Not proposed for mutable collaboration state. |

Sources: [SQLite uses](https://www.sqlite.org/whentouse.html), [WAL](https://www.sqlite.org/wal.html), [foreign keys](https://www.sqlite.org/foreignkeys.html), [types](https://www.sqlite.org/datatype3.html), [Java JDBC driver](https://github.com/xerial/sqlite-jdbc), [PostgreSQL concurrency](https://www.postgresql.org/docs/current/mvcc-intro.html), [PostgreSQL types](https://www.postgresql.org/docs/current/datatype.html), [H2 features](https://h2database.github.io/html/features.html).

## Google identity and application sessions

**Decision**: Spring Security OpenID Connect authorization-code login, with the backend owning the session. React receives application user/session state rather than persisting Google tokens.

**Rationale**: Reuse provider authentication and library validation rather than build passwords or browser-token lifecycle management. Store internal UUIDs everywhere in application relationships; uniquely map validated `(issuer, subject)` to the internal user. Google documents that subject is stable while email may change. Never merge identities by email. Profile attributes can refresh after successful sign-in.

Use only `openid email profile` for sign-in; Google API permissions and offline refresh-token access are not required. Keep CSRF protection for mutations; use HttpOnly cookies, Secure in production, and an explicit session lifetime. Any Google user may create an account, as selected by the user. Use in-memory application sessions for the prototype: sign-in again after restart, with a seven-day inactivity timeout. No Redis, database session persistence, or password recovery subsystem.

**Alternatives considered**: application passwords conflict with the user's simplified prototype scope; React-held bearer tokens add unnecessary handling; managed external identity infrastructure adds another dependency without a present need.

Sources: [Google OIDC claims](https://developers.google.com/identity/openid-connect/reference), [Google OIDC flow](https://developers.google.com/identity/openid-connect/openid-connect), [Spring OAuth2 support](https://docs.spring.io/spring-security/reference/servlet/oauth2/), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## Local and tunnel execution — environment confirmed

**Decision**: Serve built React assets with Java under one origin for Pi and local production-preview mode. Developer hot reload may use a Vite proxy to the backend. Node is a build/development dependency, not inherently a Pi production service.

**Rationale**: Shared origin simplifies cookies, callbacks, and deployment. The tunnel forwards the public HTTPS hostname to the local origin. Explicitly configure the public base URL and exact OAuth callback; do not derive security-sensitive redirect hosts from arbitrary request headers. Trust forwarded headers only from the configured tunnel/proxy path.

The user selected a single stub test identity for local execution. No Google credentials or sign-in network calls are required locally. Local data must be isolated, the fixture ID stable across restarts, and production configuration must reject fixture authentication. A separate production-profile Google smoke test covers the real provider; automated security tests can inject several identities without adding a local login selector.

**Alternatives considered**: permanently separate frontend server adds processes and cross-origin/session concerns. Native JAR/systemd is the chosen deployment shape given the existing service and Pi resource constraints. The user prefers Git pull and restarting only required components; GitHub Actions builds and tests the deployable artifact, as selected by the user. The Pi pulls the tested artifact and restarts the existing service; it does not run CI build jobs.

Sources: [Google web-server redirect rules](https://developers.google.com/identity/protocols/oauth2/web-server), [Cloudflare routing](https://developers.cloudflare.com/tunnel/concepts/routing/), [Spring proxy configuration](https://docs.spring.io/spring-boot/how-to/webserver.html), [Spring OIDC test helpers](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/oauth2.html).

## Integrity and archival rules independent of database choice

**Decision**: Preserve accepted domain rules with transactional writes and controlled-time verification.

**Rationale**: Each generated occurrence needs a unique execution key and all-or-nothing commit of occurrence completion plus entries. List deletion must serialize against acceptance and scheduled additions. Manual additions retain their own identity and creation time. Archival eligibility comes from each entry's creation time plus the list's elapsed-age threshold, not a shared weekly archive event. Background processing may scan eligible entries; active reads must never expose expired items as active.

Recipe files remain authoritative; an in-memory or persisted projection cannot become an independent editable source. Validate a complete revision before publishing it for displays or scheduled generation. Historical shopping entries retain quantities and source revision.

**Alternatives considered**: best-effort multi-file updates are inadequate for collaborative state; database-native floating-point arithmetic is unnecessary when exact ingredient totals matter.

## Confirmed delivery and prototype defaults

**Decision**: GitHub Actions performs CI for pull requests and trusted main-branch commits, builds React and Java outside the Pi, and publishes a versioned deployment bundle keyed to the full source commit SHA. The first delivery mode is operator-initiated pull on the Pi after successful CI, matching the requested Git-pull workflow. Production changes do not run directly from pull-request builds. Pin third-party Actions to commit SHAs.

**Rationale**: No exposed SSH port or CI runner on the 1 GiB Pi is required. `git pull --ff-only` followed by one deploy command downloads the successful trusted-main build for that exact commit, verifies its checksum and manifest, backs up data, switches the release, and restarts only the web service. Deployment automation must acquire a lock and keep secrets and mutable data outside Git and artifacts. Artifact retrieval uses a documented read-only GitHub credential if required by the repository and chosen artifact API. Expired artifacts are rebuilt by a trusted workflow, never silently replaced by a different commit.

**Alternatives considered**: building on the Pi uses scarce memory; a self-hosted runner mixes builds with production and requires more isolation; push deployment through SSH/tunnel requires a separate access path. Fully automatic deployment on every push is not the default.

Sources: [Workflow artifacts](https://docs.github.com/en/actions/tutorials/store-and-share-data), [GitHub CLI run download](https://cli.github.com/manual/gh_run_download), [workflow security](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions).

**Decision**: One recipe.yaml plus optional images per recipe; one shared glossary YAML, ingredient catalogue YAML, and supplied schedule YAML. Explicit typed instruction segments carry plain text, glossary references, and recipe links.

**Rationale**: This keeps authoring structured and validated without a Markdown plugin, raw HTML renderer, or automatic word matching. Glossary defaults live once and display inflections can vary at each reference. The user added optional occurrence-specific explanation overrides and standalone label/explanation annotations without glossary IDs; resolve local explanation first, then shared definition. See contracts/content-format.md for examples and validation rules.

**Alternatives considered**: YAML plus Markdown was declined by the user; inline custom markup needs a parser; requiring embedded definitions everywhere would duplicate shared knowledge; optional local overrides intentionally support recipe-specific meaning; automatic word matching can highlight unrelated ingredient names or choose the wrong meaning.

**Decision**: Account creation is open to any successfully authenticated Google user. Authorization is still membership-based for each private list. The local profile uses exactly one test identity.

**Operational defaults**: Native JAR/systemd packaging; seven-day idle in-memory sessions (restart requires re-login); SQLite-consistent daily local backups retaining seven snapshots and pre-deployment snapshots. External backup destination is operator-configured and is not assumed to exist; on-card backups do not cover SD-card loss. No data-loss or availability guarantee is claimed without a configured external copy and a tested restore.

## Pi operating constraints

The existing `systemd/mealplan-web.service` runs as `mealplan` under `/opt/mealplan`, uses `/etc/mealplan/mealplan.env`, restarts on failure, and enables ProtectSystem=strict. A database in the checkout would conflict with durable deployment separation and the read-only service policy. Use a dedicated writable state directory, keep existing tunnel port 8000, and disable the old bot service. The user confirmed Debian 12 arm64. Verify that target, the selected Java 25 runtime, and the packaged JDBC native library during deployment.

Debian bookworm provides OpenJDK 17 for ARM architectures, but the selected Java 25 runtime is provisioned from a maintained aarch64 distribution; do not assume Java 25 is available from bookworm’s default repository. References: [Debian OpenJDK 17](https://packages.debian.org/bookworm/openjdk-17-jre-headless), [SQLite JDBC supported targets](https://github.com/xerial/sqlite-jdbc), [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html).

## JVM and frontend delivery

### Backend build tool amendment

**Decision**: Use Gradle instead of Maven, as requested. Choose one backend project with Kotlin DSL and a committed Gradle Wrapper in `backend/`. Use Gradle 9.x, at least 9.1.0, because the selected Java 25 JVM must run Gradle as well as compile the application. Pin an exact supported patch and the distribution SHA-256 at implementation time; this is an implementation pinning step, not an unresolved build-tool choice. Keep Java 25 and Spring Boot 4.1.x as the application baseline.

**Rationale**: Spring Boot 4.1 supports Gradle 8.14+ and 9.x, but Gradle requires 9.1.0 or later to run on Java 25. One wrapper/version across local scripts and GitHub Actions avoids machine-specific Gradle installation drift. Kotlin DSL supplies typed build configuration without changing the Java application language or adding runtime services. Use a Java 25 toolchain and release level 25, Boot dependency management, committed dependency locks and reviewed SHA-256 dependency verification metadata. Validate wrapper integrity in CI and do not regenerate locks/verification metadata during ordinary CI builds.

**Build contract**: `./backend/gradlew -p backend check` runs all backend verification, including explicitly wired integration suites; `./backend/gradlew -p backend bootJar` creates the executable application JAR in `backend/build/libs/`. npm remains responsible for frontend dependencies/builds; `scripts/build.sh` builds React before Gradle packaging and the generated-resource dependency includes those assets in the Boot JAR. The release script identifies that executable JAR explicitly instead of selecting a plain JAR. Configure deterministic archive ordering/timestamps. Dependency/Gradle caches improve CI speed but never replace lock/checksum validation. Build on CI/developer machines; the Pi needs only Java and the release bundle.

**Alternatives considered**: Maven is superseded by the user's selection; Gradle 8.x cannot run on the selected Java 25 JVM; Groovy DSL is supported but Kotlin DSL is the chosen configuration default; a root multi-project build or frontend Gradle plugin adds coordination beyond the existing scripts without a current need; globally installed Gradle introduces avoidable version drift.

Sources: [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html), [Gradle Wrapper and checksum verification](https://docs.gradle.org/current/userguide/gradle_wrapper.html), [dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html), [dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html), [Boot Gradle packaging](https://docs.spring.io/spring-boot/gradle-plugin/packaging.html).

**Decision**: Target Java 25 with Spring Boot 4.1.x and its dependency management. The user confirmed arm64; use maintained Temurin 25 for Linux aarch64 and compile with release level 25. Document installation and updates separately from Debian 12’s default Java package. React 19.x/TypeScript assets are built into the application artifact; exact patched dependencies and Node version are locked during implementation.

**Rationale**: Boot 4.1 supports Java 25 and a maintained Java 25 runtime can be selected for the confirmed arm64 target. This new application need not retain Java 17 solely for distro packaging convenience. Keep Debian 12, provide explicit runtime provisioning, and measure actual Pi behavior. Boot 3.5 is not selected.

**Alternatives considered**: Java 17 simplifies Debian package installation but was superseded after the Java 25 discussion and arm64 confirmation; Java 21 is not needed as an intermediate target; Boot 3.5 adds an avoidable support migration; a native-image toolchain is unnecessary before measuring the regular JVM deployment.

Sources: [Debian runtime](https://packages.debian.org/bookworm/openjdk-17-jre-headless), [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Boot 3.5 final OSS release](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/).

Initial Pi validation settings are -Xms64m -Xmx256m, a small worker budget and a small SQLite pool. Target RSS is at most approximately 400 MiB for the application under household load; this is a target to measure, not a claimed result. The native JDBC extraction path must work with systemd PrivateTmp and filesystem hardening. Do not enable a hard memory limit before measuring transient startup and workload peaks.

## Java 17 versus Java 25

Boot 4.1 supports both Java 17 and Java 25. Java 17 was initially proposed for Debian 12 package availability, not because Java 25 is intrinsically too large for this Pi. With arm64 now confirmed, select Java 25 and document its maintained runtime provisioning rather than retaining Java 17 as the baseline. Do not claim faster startup or lower memory for either version without measurement on the board. Changing the Java baseline would update CI toolchains, bytecode level, local prerequisites, and service runtime together.

Sources: [Boot supported Java versions](https://docs.spring.io/spring-boot/system-requirements.html), [Debian 12 Java 17 package](https://packages.debian.org/bookworm/openjdk-17-jre-headless), [Temurin platforms](https://adoptium.net/supported-platforms), [Temurin Linux packages](https://adoptium.net/installation/linux).

## Rewrite boundary and identity

**Decision**: The user explicitly rejects backward compatibility for the rewritten application. Each recipe has one stable slug ID used in YAML, schedules, links, public endpoints, and purchase origin. No parallel numeric identity, ID translation table, previous-page redirects, response-field adapter, command wrapper, or configuration fallback is part of the design.

**Rationale**: There are no consumers requiring the previous representation. Carry forward the fifteen tutorials and requested meal/shopping/validation capabilities through the new model rather than keeping two interface systems. Content comparison fixtures verify information and rotation correctness, not exact old payloads or exit codes.

**Alternatives considered**: numeric-ID mapping, dual recipe detail routes, static menu redirects, old ingredient-availability response fields, and a compatibility CLI wrapper were removed. The Pi, cloudflared origin at 127.0.0.1:8000, and systemd service arrangement remain because the user explicitly requested them as deployment infrastructure. SQLite schema migrations, backups, and matching-release rollback support future releases of the rewritten application; they do not preserve the previous Python runtime or its interfaces.

## Schedule-model correction

The user removed the standalone fixed-week interface and clarified that new lists start unattached. The existing three-week cycle is the first catalogue meal schedule, not an implicit application-global selection. Generation and dated views require the chosen schedule identity; attachment defaults remain weekly Saturday 09:00 America/Toronto and are editable. Schedule visibility is now authenticated-only; cycle phase is chosen through a personal anchor of any weekday. Initial import now adds a one-off batch immediately for the remaining dates before the next regular addition; regular recurrence then continues unchanged.

## Authenticated personal schedule configuration

The user resolved access: recipes are public; meal scheduling requires authentication. A template supplies relative slots with no global anchor. An authenticated user signs up by choosing a template and anchor, creating a personal configured schedule for daily meals independently of a list. A list owner may import that configuration for automatic purchases. Public dated-template previews and a shared global anchor are removed. Live import is selected: future shopping generation follows the personal schedule’s current template/anchor; historical entries stay unchanged and list addition settings remain independent. Any calendar date anchors cycle day 1; meals and rest days shift together (confirmed by the user). Immediate import generation is confirmed for the remaining dates up to the next addition date, exclusive. The user selected no assignments before the anchor: show not_started, exclude those dates from all purchase batches, and complete entirely pre-anchor batches empty without shifting recurrence. The user selected deletion with detachment: warn about all affected lists, remove the personal configuration, stop all linked additions, and preserve lists and existing purchases. Competing generation must recheck source/link state transactionally.

## Approved analysis resolutions

I1: the five-second shared-list guarantee applies to visible connected views; hidden views stop polling and fetch immediately when visible. A1: keep each attachment’s last successfully covered period and skip dates before its exclusive end; advance it atomically with successful generation, preserve it across addition-setting edits/item actions, and clear every linked marker when the personal anchor actually changes. Historical entries and occurrence retry identities remain unchanged; no inventory/quantity deduction is inferred from checklist actions.

A3: use a calendar month rather than a fixed 30 days. User-facing date queries have both a one-month span and a horizon of one month ahead, with clear 422 errors for excessive explicit requests. Automatically derived previews are clipped/labeled partial; internal jobs and trusted operator commands are not truncated. A2 is resolved by the user: each viewer’s current local time zone controls personal and shared-list daily views and their query horizon. The browser supplies the current IANA zone; no owner-specific saved zone is needed. Anchor/meal dates remain date-only, while the attachment’s independent configured zone continues to control actual additions.

I2: move initial recipe/personal-schedule router integration to US1 and extend it for shopping in US2. C1: explicitly test successful populated V003/V004→V005 upgrade, new FK enforcement and migration-failure rollback. I3: the plan summary now labels schedule endpoints authenticated.
