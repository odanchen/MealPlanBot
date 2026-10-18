# User story 4 verification

Implemented T054–T075 on 2026-10-18. Requirements checklist: 16 checked, none
unchecked. No extension configuration or implementation hooks apply.

## Delivered behavior

One live personal-schedule attachment per list, enforced by a partial unique index.
Owners explicitly import, replace, configure, pause or detach the link. An import
creates a durable initial occurrence and immediately adds the uncovered interval
from its local import date to the next regular addition date (exclusive). Recurrence
has independent day/week interval, weekday, time, IANA zone and start reference.
Saturday 09:00 America/Toronto is the default. Future-only anchors and same-date
initial intervals complete empty without shifting the regular recurrence.

One shared exact planner clips dates before the source anchor and before the last
successfully covered exclusive end. Rest days and instruction-only links contribute
no purchases. Quantified optional purchasing ingredients are included; unquantified
suggestions have no invented quantities. Compatible quantities aggregate
inside each batch, while separate occurrences/manual entries remain separate.
A bunch remains an explicit count and is never converted to an inferred mass.

Generation validates one content snapshot and current source/link/coverage state
inside the SQLite write transaction before inserting any entry. Entry creation,
coverage advancement, snapshot metadata and occurrence completion commit together.
Failed content validation adds nothing or advances no coverage. Retrying reuses the
same occurrence. Unique occurrence keys and per-attachment striped locks prevent
duplicate batches. Pending durable work is recovered; downtime advances to the latest
due period rather than replaying obsolete periods. New entries use actual creation
time. Settings edits retain coverage; actual source-anchor changes clear all linked
markers without deleting purchases or replaying completed occurrences.

Personal-source deletion names affected lists, atomically detaches/disables all of
them, clears live source references and preserves purchases and occurrence history.
List deletion instead cascades its attachments, occurrences and entries. Authorization
is rechecked in the transaction so generation/retries cannot resurrect deleted state.

Items expire independently at creation time plus the list's elapsed-time age limit,
regardless of bought state. Reads enforce eligibility and materialize due rows; a
bounded sweep handles up to 500 due rows. Age-policy changes first archive rows due
under the old policy, then apply the new policy to remaining rows. Extension cannot
resurrect archived entries; days are 24 hours and weeks seven days.

Members see effective dated meals, coverage, next due time and generation outcomes;
only owners configure/retry. Dated APIs require the viewer IANA zone and existing
calendar-month bounds. Derived previews retain full candidate-period metadata,
exclude prior coverage and clearly label clipped totals or beyond-horizon periods.
Display zones never modify addition instants or persisted generation state.

React controls cover live imports/replacements, settings, detach, failures/retries,
viewer-local previews, source deletion warnings and archive age limits. Trusted
`preview-meals` and `preview-shopping` commands reuse the resolver/planner without
starting authentication or accessing a database.

## Verification

`./scripts/check.sh`: PASS (59 backend tests, ten frontend tests, eight browser
journeys, content validation, locked installation/lint/typecheck and JAR packaging).
A final focused lifecycle suite additionally verifies list-deletion cascades.

- Controlled-clock tests: strict next due, daily/fortnightly intervals, DST gap's
  first valid local instant, first repeat offset, pre-anchor/empty initial batches,
  immediate import, retry identity, no partial failures and latest-only catch-up.
- Coverage tests: recurrence edits retain/exclude prior coverage, rest-only slices
  advance coverage, anchor changes clear markers, and viewer zones with different
  local today values clip previews differently without changing same-date meals,
  next due, source state or coverage.
- SQLite migration: populated V003/V004 upgrades retain manual quantities, checked
  and archived state and memberships; V005 enforces occurrence foreign keys. A
  deliberately failed upgrade rolls back without losing original data.
- Lifecycle: multiple links detach on source deletion while purchases remain;
  replacement leaves one active attachment; three concurrent generation/deletion
  races add no purchases after deletion returns. List deletion removes occurrence
  history while retaining the independent personal source; later retries fail.
- Age: exact independent seven-day expiry, bought-state independence, reduction,
  extension without resurrection and preserved newer entries.
- Real-browser journeys cover live import/initial batch, repeat-safe regular runs,
  two viewer zones, another member's inability to read the private source, live
  anchor change, replacement and multi-list source deletion. Another journey
  injects a missing source template through test-only infrastructure, verifies no
  entries, restores it and retries the same occurrence; it checks clipped previews,
  oversized query rejection and two independently aged manual entries.
- Packaged commands: 22 dated meals include pre-anchor not_started; 21-day shopping
  preview returns distinct ingredient/unit totals with decimal string quantities.
- Browser clock controls run serially against disposable state. Test-only controls
  remain outside packaged application classes. No dependency locks, checklist
  markers or production state changed.

## Limits

Actual Pi latency/memory, live Google sign-in and representative-user studies remain
operational acceptance tasks. No deployment ran. Glossary interaction and measurement/
shopping view controls remain US5/US6 at this checkpoint.
