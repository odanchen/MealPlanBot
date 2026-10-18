# Setup and Foundation verification

Verified on 2026-10-18, limited to T001–T017. T018–T102 remain unchecked. Production services, the existing Python runtime, systemd files, recipe content and production state were not changed. No deployment occurred.

## Working foundation

The pinned Gradle wrapper builds a Java 25/Spring Boot backend and packages the React shell in its executable JAR. Local mode uses one stable fixture UUID and isolated `.local/mealplan/` SQLite state without Google credentials. The frontend has session context, anonymous/public navigation, protected-page sign-in guidance, a typed CSRF/version/idempotency HTTP client, and browser-zone/date helpers. Recipe/schedule/list features remain placeholders until their story tasks.

SQLite uses two connections with foreign keys, WAL, synchronous=FULL and a 3-second busy timeout. Migrations and identity provisioning are transactional. Google identities map issuer/subject to application UUIDs, independently of mutable email. Exact quantities retain decimal strings and aggregate only compatible dimensions and explicit package sizes.

## Verification results

| Check                                                  | Result                                                                                                            |
| ------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| Requirements checklist                                 | 16 checked, 0 unchecked; file unchanged                                                                           |
| `./scripts/check.sh`                                   | PASS: npm locked install, lint, TypeScript, components/accessibility, browser checks, backend check               |
| Backend suites                                         | 28 tests, zero failures/errors, actual SQLite JDBC engine                                                         |
| Frontend suites                                        | 8 component/client tests and 2 Chromium browser tests passed                                                      |
| `./scripts/build.sh` and final wrapper `check bootJar` | PASS; executable `backend/build/libs/mealplan-0.1.0.jar` contains generated `static/index.html` and hashed assets |
| Dependency metadata                                    | Gradle lockfile, verification metadata and npm lockfile SHA-256 unchanged during ordinary checks/builds           |
| Source whitespace and scope                            | `git diff --check` passes; legacy runtime/content/service paths unchanged                                         |

Backend checks cover both SQLite connections' durability settings, populated persistence/reopen, exact TEXT amounts and integer epoch milliseconds, migration idempotence/checksums, failed-migration/write rollback preserving prior rows, foreign-key rejection, concurrent first sign-ins, stable UUIDs across profile changes/restart, distinct subjects with the same email, unauthenticated denial, CSRF, logout, safe errors and version conflicts. Quantity cases cover metric/US weight and volume, fractional sums, counts, distinct products and explicit package sizes without inferred conversion.

An isolated packaged-JAR smoke check used a disposable ignored database and free loopback port 8000. It passed public shell/deep links, unknown asset/API error behavior, anonymous denial, actual CSRF-token login/logout, local cookie flags, process restart invalidation, persistent fixture UUID, Secure/HttpOnly/SameSite=Lax production-style cookies, and explicit Pi-target rejection of the local fixture profile. Every test application process was stopped. No Google requests or production state were used.

Canonical OAuth callback/scopes, trusted OIDC principal mapping, cancellation and safe return paths are tested without contacting Google. Runtime validation enforces seven-day inactivity, memory-only sessions and production cookie safety. The test profile requires an explicitly supplied disposable database path and cannot fall back to production state.

## Remaining limits

No known blocking Foundation failures remain. Real Google sign-in through the HTTPS tunnel and actual arm64 Pi resource/performance checks remain unverified and belong to later acceptance tasks. Content validation joins `scripts/check.sh` in T032. npm reports zero known vulnerabilities; optional fsevents install scripts are blocked, and test tooling emits a non-failing JDK instrumentation warning.

For prerequisites and local commands, see [development.md](../development.md). No US1 implementation has started.

## Subsequent legacy cleanup

After the Foundation verification above, the user explicitly requested removal of
the legacy Python implementation. Its source, tests, dependency metadata, local
virtualenv/caches and service templates were removed from the checkout. Original
recipe content and assets were retained. No installed production service was
changed, and T097 remains open for its remaining content/cutover requirements.
