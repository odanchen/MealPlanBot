# User story 1 verification

Implemented T018–T033 on `001-upgrade-recipe-planner`, 2026-10-18.
The requirements checklist passed with 16 checked items and remains unchanged.
No extension configuration exists, so no before/after implementation hooks apply.

## Delivered behavior

All fifteen tutorials now have one authoritative YAML file each. Shared ingredient
identities/aliases/categories, cooking definitions, and the 21-slot template are
validated together. The original HTML/JSON/manifest copies were removed only after
the captured content comparison passed. The content-only fixture retains the
original titles, purchasing quantities/units, serving and timing estimates,
preparation guidance and all 71 ordered steps; it stores no former numeric IDs or URLs.

Public recipe APIs and shared React pages show full metadata, categorized
ingredients and instructions without image placeholders. Previous/next wraps in
rotation order; optional validated assets are served only for their owning recipe.
Glossary labels and recipe links receive safe basic rendering; interactive glossary
controls and measurement conversion UI remain US5/US6 work.

Authenticated users can discover the supplied template, create a private schedule
with any calendar anchor, list/read it, update its anchor using If-Match, and view
meal/rest/not_started dates. SQLite stores ownership, date-only anchors, timestamps
and versions. Viewer IANA zones govern local today and calendar-month query bounds;
the same requested calendar date retains the same relative assignment in every zone.
The browser refreshes dated data on viewer day/zone changes, visibility and focus.

The credential-free Gradle validator and packaged `validate-content` entry point
reject invalid content. Accepted snapshots are immutable; a failed revision leaves
the previous snapshot intact. Startup loads a complete accepted revision; normal
content authoring requires application restart. `/health` reports readiness,
revision and the configured commit (`mealplan.commit`, default `development`).

## Verification

Final `./scripts/check.sh`: PASS (locked install, lint, typecheck, components,
backend check/content validation, executable JAR build, Chromium acceptance).

- Backend: 36 passing tests against the actual SQLite engine, including all foundation
  tests plus content preservation, source-update projection, rejected duplicate/unknown
  fields, tags, numeric/nonpositive quantities, unsupported units, invalid references,
  unsafe/missing/symlink assets, alias collisions, and valid optional-asset delivery.
- HTTP/security: anonymous recipe access, private schedule denial, another owner's 404,
  CSRF, optimistic version conflict, far-future anchors, complete cycle/pre-anchor
  evaluation, missing/invalid viewer zones and explicit range/horizon rejection.
- Calendar boundary: January 31 end-of-month clamping, inclusive final allowed date,
  and different local today across Kiritimati/Honolulu at the same instant.
- Frontend: lint/type checks and eight existing component/client tests pass.
- Chromium: three acceptance tests pass. The real local backend workflow opens all
  fifteen tutorials, verifies wraparound, creates/changes/refreshes a private schedule,
  checks meal/rest/not_started/horizon states and confirms logout revokes API access.
  Local fixture sign-in is automatic on page bootstrap, as established in Foundation.
- A 320-pixel recipe screenshot was inspected; the automated layout check finds no
  horizontal overflow. Missing images produce no broken placeholder.
- Packaged validator succeeds without credentials/database and exits 1 with a concise
  error for a missing content directory. The executable JAR includes the frontend.
- Dependency lockfiles and Gradle verification metadata remain unchanged.

The first recipe contract check failed before implementation. Later checks caught
and resolved a Spring repository proxy constraint, a missing Vite CSS declaration,
and a directory/rotation ordering mismatch. Browser expectations were corrected to
follow the existing automatic local fixture authentication behavior.

## Remaining scope

Shopping lists, recurring additions, source deletion/detachment, interactive glossary
bubbles and measurement controls remain in later story tasks. No production service
or production state was changed. Real Google/tunnel sign-in, Pi resource measurements,
backup/restore and production cutover remain later operational acceptance work.
