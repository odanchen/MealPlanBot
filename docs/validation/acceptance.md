# Current release acceptance

Local implementation verification: 2026-10-18. The final full check passed 76 backend
tests, 13 component tests and 13 Chromium journeys, with no failed or skipped backend
tests; formatting, lint, type checking and content validation also passed. All six stories have their detailed
evidence in `us1.md` through `us6.md`. Release/security/recovery evidence is in
[security.md](security.md) and [recovery.md](recovery.md). No actual Pi, Google/tunnel,
installed-service cutover or participant result is claimed.

## Functional requirement evidence

| Requirements  | Local evidence                                                                                                  | External limit                                  |
| ------------- | --------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| FR-001–FR-004 | Content migration/validation, immutable snapshots, schedule/query bounds; unsized package rejection             | None for automated content rules                |
| FR-005–FR-006 | Glossary precedence, CookingTerm accessibility/narrow viewport and reusable-method navigation                   | Representative-user usability pending           |
| FR-007–FR-009 | Exact Quantity/aggregation/conversion tests, canonical ingredient aliases/categories, weekly totals             | None for tested arithmetic                      |
| FR-010        | Identity/security/session tests, isolated stable local account                                                  | Real Google sign-in/cancellation/logout pending |
| FR-011–FR-014 | PrivateList, mutations, memberships/invitations and browser journeys                                            | Live Google invitation journey pending          |
| FR-015        | Concurrent SQLite writes/conflicts, polling/hidden/reconnect Chromium journeys                                  | Target ten-user latency/visibility pending      |
| FR-016        | All four sort/group combinations, local/remote checkbox focus, every-entry/state retention                      | Unassisted participant outcomes pending         |
| FR-017–FR-019 | Schedule import, recurrence, live anchors, exact coverage and occurrence deduplication                          | Target operation pending                        |
| FR-020–FR-021 | Exact per-item age boundary, retained archive state and member clearing                                         | Target operation pending                        |
| FR-022        | Pending downtime skip versus explicit failed retry, overlap clipping and member-visible policy                  | Target downtime behavior pending                |
| FR-023        | Saved historical quantities versus accepted-source future generation                                            | None for tested rules                           |
| FR-024        | New read-only/preview/validation contracts, no Python/Telegram runtime in checkout, bot-disable cutover tooling | Installed bot retirement pending                |
| FR-025–FR-026 | Leave/delete authorization, invitation invalidation and SQLite deletion races                                   | Installed service acceptance pending            |
| FR-027–FR-028 | Issuer/subject UUID mapping, profile/session tests, packaged fixture rejection                                  | Live Google and Pi isolation pending            |
| FR-029–FR-030 | YAML/shared renderer, refreshed affected-list deletion warning and source-deletion races                        | Unassisted user study pending                   |

## Success criteria evidence

| Criteria      | Status                                                                                                                            |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| SC-001–SC-002 | Automated content preservation/revision and all 21 cycle slots verified                                                           |
| SC-003        | **Pending**: ten users; ≥9 complete registration/list/first item in ≤3 minutes unassisted                                         |
| SC-004–SC-007 | Local privacy/expiry, complete repeat-safe batches, independent expiry, exact conversions and accessible explanations verified    |
| SC-008        | **Pending**: actual Pi ten-member/500-entry workload and connected-view visibility                                                |
| SC-009        | All four ordering/grouping combinations preserve entries/states; **pending** ≥9/10 unassisted ingredient/term/check/archive tasks |
| SC-010        | Automated departure/deletion races and token invalidation verified                                                                |
| SC-011        | Application workflows run without Telegram locally; **pending** installed bot stop/disable evidence                               |
| SC-012        | Personal-source privacy, any-date/pre-anchor/import/delete lifecycle verified                                                     |

## Quickstart scenario coverage

| Scenario               | Evidence/status                                                                                       |
| ---------------------- | ----------------------------------------------------------------------------------------------------- |
| 1 Content preservation | ContentMigrationTest/ContentValidationTest and US1 evidence                                           |
| 2 Glossary             | GlossaryValidationTest/CookingTerm component and cooking-terms Chromium journey                       |
| 3 Exact quantities     | Quantity/IngredientAggregator/measurement and shopping preview checks                                 |
| 4 Privacy/sharing      | Membership/invitation/security suites and shared-shopping journey                                     |
| 5 Collaboration        | SQLite concurrency and visible/hidden/reconnect browser checks; target load pending                   |
| 6 Recurrence           | Controlled-time ScheduleGenerationTest/RecurrenceCoverageTest and schedule-shopping journey           |
| 7 Per-item archive     | ItemAgePolicyTest, policy change/member visibility and archive browser checks                         |
| 8 Races                | ShoppingConcurrencyTest/ScheduleLifecycleTest and refreshed deletion warning journey                  |
| 9 Production isolation | IdentitySecurityTest and real packaged Pi-profile fixture-rejection/safe-log checks on local hardware |
| 10 Pi measurements     | Pending; see pi-performance.md                                                                        |
| 11 Recovery            | Real packaged migrations, online WAL snapshot, matched rollback; see recovery.md                      |
| 12 Query bounds        | Personal/list HTTP bounds, viewer-zone tests and browser journeys                                     |

T102/T119/T133 stay open until actual Google/Pi/cutover prerequisites and timed
participant results exist. T094/T115/T129 retain their actual PrivateTmp verification
gate. Checklists remain read-only; only verified implementation tasks are checked.
