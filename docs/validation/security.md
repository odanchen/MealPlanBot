# Integrated access-boundary validation

Validated locally on 2026-10-18 against disposable SQLite, the test-only browser server,
and the executable Java JAR. No production credentials, target state or Google requests
were used.

`AccessBoundaryTest` verifies anonymous 401/no-store, foreign-member 404, CSRF 403,
fragment-only invitation links, safe health diagnostics, removed numeric/global recipe
and schedule interfaces, and unusable invitation acceptance after list deletion.
The existing `ShoppingConcurrencyTest`, `InvitationMembershipTest`,
`ScheduleLifecycleTest` and `IdentitySecurityTest` supply actual-SQLite deletion races,
exact invitation expiry/withdrawal, source detach races, UUID mapping, session and
fixture isolation. Their checks are reused rather than duplicated.

`frontend/tests/e2e/access-boundaries.spec.ts` verifies private-route denial, CSRF,
explicit sign-in gating, and removal of invitation fragments before later requests.
`shared-shopping.spec.ts` covers foreign access, leave/delete, withdrawn/expired links,
multiple principals, disconnected and hidden views. Browser principal headers and clock
controls exist only on the test runtime classpath.

`scripts/test-packaged-boundaries.sh` actually starts the executable JAR with the Pi
profile on local loopback. It verifies anonymous/forged-test-header denial, mutation
CSRF, public shell, numeric-alias rejection, unavailable test infrastructure, no-store,
no private request or fake secret in normal logs, and rejected Pi fixture startup.
The packaged ZIP inventory contains no browser/test classes. This run uses dummy
client identifiers and makes no OIDC request.

Production Google sign-in, HTTPS cookie behavior through the actual tunnel and the
Pi's hardening/native extraction remain external acceptance in the open tasks.
