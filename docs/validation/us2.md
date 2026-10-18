# User story 2 verification

Implemented T034–T043 on 2026-10-18. The requirements checklist remains unchanged:
16 checked, zero unchecked. No `.specify/extensions.yml` exists, so no pre/post
implementation hooks apply.

## Delivered behavior

Signed-in users create named private shopping lists and see only lists where they
are members. V003 atomically stores the list and owner membership with the default
604800-second age limit, no schedule, and no automatic additions. Ownership uses
the existing stable internal user UUID rather than email.

Each manual addition has its own UUID, creation time, optional canonical decimal
quantity/unit pair, contributor, and unchecked state. Exact ingredient names and
curated aliases resolve to canonical IDs; free text remains usable. Quantities and
origin are immutable: remove and add to change a purchase need. Members can set
bought/unbought explicitly and remove entries. Version preconditions reject stale
writes, and a removed item cannot be restored by a stale request.

Create request IDs are scoped to user and operation. Their payload hashes and
serialized responses commit in the same SQLite transaction as the mutation.
Retries return the saved result for seven days, and conflicting payloads return
409. A different key deliberately creates another independent entry. Replay uses
the original submitted payload before content-based ingredient resolution.

The React catalogue/detail routes include labelled forms, inline errors, optional
quantity/unit controls, empty/unattached states, checkboxes, and removal confirmation.
Invalid input is retained. Confirmation supports Escape, focus containment, focus
restoration on cancellation, and focus in the item-name field after removal.
Private deep links display the sign-in shell and retain their list UUID through
sign-out/sign-in. Production continues to use Google only; the local fixture needs
no Google credentials or password.

## Verification

`./scripts/check.sh`: PASS (locked npm install, lint, typecheck, frontend tests,
backend check/content validation, packaged JAR, all Chromium journeys).

- Backend: 42 tests pass using actual SQLite. Shopping coverage includes anonymous
  and nonmember denial, member-only catalogue, atomic creation, paired positive
  quantities, distinct duplicate entries, CSRF, required request IDs/versions,
  conflicting retry payloads, explicit bought-state idempotence, stale writes,
  deleted-item safety, and invalid sorting.
- A separate SQLite close/reopen test verifies persisted ownership, contributions,
  entries and retry results after the Google identity's email changes. A controlled
  clock verifies replay at seven days minus one millisecond and fresh mutation at
  the exact seven-day boundary. A deliberately failed mutation rolls back its entry
  and retry record together.
- Frontend: lint/typecheck and all eight component/client tests pass.
- Chromium: all four journeys pass. The new real-backend shopping journey creates
  a list, rejects a negative quantity while retaining input, adds the same ingredient
  independently, checks/unchecks, refreshes, cancels/confirms removal, signs out,
  verifies anonymous API denial, signs in locally, and finds the persisted list/items.
- The shopping page has no axe accessibility violations at 320 pixels and no
  horizontal overflow. Its mobile screenshot was visually inspected.
- After the final dialog focus adjustment, lint/typecheck, JAR packaging and the
  focused shopping browser journey were rerun and pass, including the post-removal
  focus assertion.
- Dependency locks, verification metadata, content files and checklist markers are
  unchanged. Ignore files already cover the Java/Node setup; ESLint ignores build,
  dependency, coverage and browser-output directories.

## Limits

Live Google sign-in and the ten-person usability study were not run. Existing
foundation tests exercise trusted OIDC identity mapping, cancelled sign-in, session
invalidation and provider configuration without contacting Google. Sharing,
invitations, polling, recurring additions and automatic archival remain later
story work. No production deployment or production state was changed.
