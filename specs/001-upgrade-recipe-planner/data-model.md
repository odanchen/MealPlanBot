# Data Model

SQLite is the only mutable-state engine in every environment. Recipe/ingredient/glossary/schedule files are authoritative content, with immutable validated in-memory projections. Historical purchases store source identifiers and content revision without database foreign keys into mutable external content.

## Storage conventions

Viewer-local presentation uses the browser/device’s current IANA zone supplied per request, not a saved owner/schedule zone. Do not add a personal-schedule time-zone field solely for daily presentation. UTC is the explicit labeled client fallback if zone detection is unavailable; the server validates a supplied zone and never silently uses the host zone. Anchor and meal dates are date-only labels, not midnight instants to convert between zones. Each viewer’s zone determines today and their query horizon. Attachment.time_zone independently controls addition instants and batch coverage; changing display zone changes no persisted generation state.

- Application entity IDs: randomly generated UUID strings, never emails. Content IDs: stable slugs used consistently by recipe files, schedule references, API responses, links, and purchase origin. No parallel numeric recipe identity or old-ID mapping.
- Instants: integer UTC epoch milliseconds. Local recurrence fields: calendar date, HH:mm, named IANA zone. Days for expiry are 24 elapsed hours.
- Quantities: canonical positive decimal TEXT parsed with BigDecimal, not SQLite REAL. Aggregate exactly before display conversion/rounding; stored purchase quantities do not change with display system.
- Enable foreign_keys on every connection, WAL on local disk, synchronous=FULL, a bounded busy timeout and short write transactions. Start with two connections and bounded request concurrency; verify on the Pi.
- Explicit versioned SQLite migrations run once under the deployment lock. Never use an H2 substitute for migration/persistence tests.

## Entities

### User

`id` UUID primary key; `display_name`, `current_email` nullable profile data; `created_at`, `last_login_at`. Email need not be unique and is never referenced by ownership/membership foreign keys. No password fields.

### ExternalIdentity

`issuer`, `subject` unique pair; `user_id` foreign key; timestamps. Validate Google issuer/token claims before mapping and normalize only documented equivalent Google issuer values. Updating email retains the same user. Never link a different subject merely because its email matches. Atomically create the identity and user on first production sign-in; races use the unique constraint.

Local profile seeds one fixed UUID and local fixture identity in a separate local database. The production profile cannot activate fixture authentication. Multi-user tests inject test principals through test support, not the local user interface.

### ShoppingList

`id`, `name` nonblank, `owner_user_id`, `created_at`, `item_age_limit_seconds` positive; `version`. Creation transaction inserts the list plus owner membership and no schedule attachment. Without an attachment, no automatic ingredients are generated. Owner remains its creator; ownership transfer excluded. Default age limit 604800 seconds. The list has no archive recurrence.

### Membership

Composite primary key `(list_id, user_id)`, joined_at. All list item operations require membership; owner-only settings and deletion require owner identity. Nonowners can delete their own membership; contributions remain. Membership removal revokes later reads and writes immediately.

### Invitation

`id`, `list_id`, `token_hash` unique, `created_by`, `created_at`, `expires_at = created_at + 72 hours`, `withdrawn_at` nullable. Store only a hash of a random high-entropy token. Multi-use until strictly before expiry; repeated acceptance by an existing member is idempotent. Creation/withdrawal owner-only. List deletion removes/inactivates all tokens.

### ShoppingEntry

`id`, `list_id`, `display_name`, nullable `ingredient_id`, nullable paired `quantity_decimal` and `unit`, `created_at`, nullable `bought_at`, nullable `archived_at`, nullable `created_by`, nullable `occurrence_id`, `source_content_revision`, `version`.

Quantity/unit are both supplied or both absent. Manual entry has its own ID/time even if another entry matches; always starts unchecked. Each generated batch contains one total per canonical ingredient and compatible unit dimension/package type. Entry age does not reset when checked. Materialized quantities and origin are immutable in this prototype; changing a purchase need uses removal plus a new addition.

Active → archived when `now >= created_at + current list age limit`; bought/unbought is independent. Archived → cleared by permanent removal; active → removed by permanent removal. No restore. Persist archival monotonically; active views first materialize eligible transitions or otherwise exclude due entries. Before an age-limit change, materialize entries already due under the old limit, then apply the new limit to remaining active entries. This prevents a longer limit from resurrecting already-expired items. Use one transaction for that change.

### ConfiguredUserSchedule

`id` UUID primary key, `user_id` owner foreign key, `template_id` content slug, `anchor_date` chosen local date, `created_at`, `version`. This is a user signup/configuration, not authoring a new template. It exists independently of shopping lists and has no application-global anchor. The owning authenticated user can view its dated meals; other users have no direct access to it. Any weekday is valid. The anchor is cycle day 1; for dates on/after it, `slotIndex = DAYS.between(anchor, date) % template.cycleLength`. The supplied 21-slot sequence is five meals then two rest days, repeated three times; all slots shift with the anchor. Do not enforce a Sunday date or fixed calendar weekdays for rest days. Dates before anchor have not_started status and no cycle index; never apply negative modulo to repeat backward. The owner may delete the personal configuration after being warned about linked lists. Atomically detach all linked attachments and stop future generation while preserving lists, members, entries, and purchase origin.

### ScheduleAttachment

Zero or one nondetached import per shopping list, enforced by a partial unique index on list_id where detached_at is null; list creation adds none. The owner imports one of their configured user schedules. Required logical state includes attachment ID, list ID, nullable source configured-schedule ID foreign key (required while attached), nullable detached_at, enabled flag, addition interval/count, local time, time zone, recurrence start reference, optional weekly weekday, configuration version, next due instant, nullable last_covered_start_date and last_covered_end_date_exclusive (both absent or both present with start < end), and entity version. The recurring-addition start reference is distinct from the meal-cycle anchor.

Import is a live relationship. Resolve effective template and meal anchor from ConfiguredUserSchedule rather than storing an independently editable duplicate on the attachment. Source edits affect future generation and meal previews, not list-specific recurrence or already-created entries. An actual anchor-date change atomically updates the source version and clears the covered-period marker on every linked attachment; a no-op edit does not reset it. Completed-occurrence identities and historical entries remain intact. Generation snapshots recheck the source version before committing, so an old-anchor batch cannot restore a cleared marker. Source deletion/unsubscription is allowed after a warning. In the same write transaction, disable and mark all linked attachments detached, clear the source reference, and remove the personal configuration. Retain attachment/occurrence history and its immutable effective template/anchor snapshots so existing entries keep their origin; this history is not a live or editable fallback schedule. Do not cascade source deletion to entries or lists. Members can view the effective list schedule without gaining access to the source user’s personal schedule catalogue.

Import creates a separately identified initial occurrence immediately. It covers `[import local date, first subsequent regular due local date)`; the first regular due instant is strictly after import. If both dates are equal (for example Saturday before 09:00), the initial range is empty and completes with no entries; the regular run still covers that date. The regular recurrence is not shifted.

Local calendar arithmetic defines addition recurrence rather than fixed-hour offsets. Each occurrence covers local dates `[scheduled due date, next scheduled due date)` using the imported effective template and anchor. A generation snapshot records the source configured-schedule ID/version, effective template and anchor, and addition configuration; it determines the covered dates and ingredients consistently. Rest days and dates before the linked personal anchor add no ingredients. Intersect each occurrence’s date range with `[anchor_date, infinity)` for meal selection without changing its recorded coverage or recurrence. If no meals remain, complete that occurrence with zero entries rather than treating it as missing data. For the unchanged anchor, compute effective coverage start as max(candidate start, anchor_date, last_covered_end_date_exclusive when present). A candidate ending at/before that start completes with no entries and retains the existing marker. For a successfully evaluated nonempty date slice, record its start/end atomically with its entries and completed occurrence, even if all its dates are rest days and produce zero entries. Pre-anchor-only intervals do not create a coverage marker. Failure never advances it. A recurrence/time-zone edit preserves the marker; checking/removing/archiving items also preserves it. This is date-coverage bookkeeping, not inventory accounting. The existing latest-only downtime policy is unchanged and does not backfill obsolete periods. Changing addition settings schedules future work without replaying completed occurrences; disabling cancels future generation.

### ScheduleOccurrence

`id`, `attachment_id`, `kind` initial/recurring, `scheduled_at` (import instant for initial), `configuration_version`, `coverage_start_date`, `coverage_end_date_exclusive`, `time_zone`, `content_revision`, `source_schedule_version`, `effective_template_id`, `effective_anchor_date`, `status` pending/failed/completed/skipped, sanitized `failure_code`, `completed_at`. Unique `(attachment_id, kind, scheduled_at)` plus one initial occurrence per attachment prevents duplicate batches. Import-request idempotency reuses that attachment and initial occurrence instead of creating another batch. Initial validation failure adds no entries and exposes the same retryable failed occurrence; regular recurrence remains configured.

Validation occurs against one content snapshot. In a short write transaction recheck list existence, attachment enabled/version, linked personal-schedule version, and unique occurrence key, reload the current coverage marker, clip already covered dates, create entries, advance the marker if appropriate, and mark completed in the same transaction. A concurrently advanced marker requires recomputing the candidate plan before commit; it cannot be overwritten by an older snapshot. Failure inserts no purchase entries. Retrying the same failed occurrence may succeed exactly once. No durable partially written batch. Per-attachment locking plus SQLite uniqueness resolves scheduler races. Restart catches up only the latest missed occurrence; earlier missed occurrences are marked skipped or advanced past, without adding obsolete batches. New entries age from actual creation, not historical due time.

## Concurrency and deletion

Require version preconditions on destructive/settings/item-state changes. Different-entry writes can both succeed; stale same-entry writes return conflict and current state for refresh rather than silently overwriting. An explicit set-bought value is idempotent; do not use a blind toggle command. Create operations use client request IDs scoped to authenticated user and operation, backed by a small durable deduplication record, to distinguish a network retry from a deliberate duplicate manual addition. Reusing a request ID with a different payload is a conflict. Retain deduplication keys seven days; retries after that are outside the guarantee.

Personal configured-schedule deletion runs in one transaction that rechecks ownership, detaches/disables all its imports, and removes the source. A generation transaction must recheck source existence and attachment enabled/nondetached state at commit; a deletion that commits first prevents that generation. A batch committed earlier remains a historical purchase. Detachment permits a later explicit import to create a new attachment/initial occurrence.

Owner deletion of a shopping list runs in one write transaction: remove list and cascade memberships, invitations, entries, attachment, and occurrences. Recheck existence within every competing transaction. No later member write, invite acceptance, or scheduler run can restore the list. Delete/leave access changes appear to other connected clients through the next automatic refresh, while authorization is checked immediately on every request.

## Other state

MealScheduleTemplate is content with stable slug ID, name, cycle length, and ordered day slots, without an absolute calendar anchor. Initially only the supplied 21-slot template exists. Creating templates is outside the prototype; configuring a personal schedule with an anchor is included. Template discovery and configured dated views require authentication, while individual recipes remain public.

In-memory server sessions: seven-day inactivity timeout, invalidated on logout and application restart; account/list persistence is unaffected. No persistent session service.

Read-only recipe projection: title, servings, timing, image paths/alt text, ingredient references and amounts, ordered sections, text/term/recipe segments, and source revision. Explanation controls carry an optional glossary ID, optional local label, and optional local explanation. At resolution, use local label/explanation first and fall back to the referenced glossary values; no glossary ID requires both local fields. Explicit term IDs must resolve, including when overridden. Reject blank overrides. The source recipe owns local explanations and the glossary owns shared defaults; neither edits the other. Return the resolved plain-text label/explanation with the immutable recipe snapshot. Glossary terms and ingredient aliases are validated once per accepted content revision. Projection replacement is atomic and cannot publish an incomplete catalogue.
