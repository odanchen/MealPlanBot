# Production Google acceptance: pending

No live OAuth credentials/tunnel/target session was used during the 2026-10-18
implementation. T101/T111/T126 remain unchecked. Local claim-mapping/security tests
and packaged Pi-profile startup with dummy credentials do not establish live sign-in.

On the configured canonical HTTPS origin, record the release SHA and exact callback
`/login/oauth2/code/google`; two Google accounts must receive distinct immutable UUIDs,
retain private lists across sign-in, explicitly accept invitations, and lose access on
logout. Exercise cancellation/error and restart sign-in. Inspect HttpOnly/Secure/
SameSite=Lax cookies, openid/email/profile scopes, same-subject changed-email mapping,
and rejected fixture authentication. Keep credentials and invitation tokens out of
screenshots and logs. Record only observed outcomes before checking the tasks.
