# HTTP, Authentication, and Operator Contract

Design contract for implementation, not evidence of existing endpoints. JSON uses camelCase; Application entity IDs are UUID strings; recipe, term, ingredient, and schedule IDs are stable content slugs. Recipe IDs have no numeric aliases or second identifier. Dates are ISO local dates; instants are ISO UTC strings. Decimal quantities are strings. Errors use `{code, message, fieldErrors?, currentVersion?}` without secrets or private data from inaccessible lists.

## Origin and security

Production binds 127.0.0.1:8000 behind the existing tunnel; PUBLIC_BASE_URL supplies canonical HTTPS links and OAuth callback URLs. React build and API share an origin. Static assets may be cached by content hash; authenticated responses use Cache-Control: no-store. Local mode binds loopback and uses a separate SQLite path and one stable stub identity. Pi service declares its deployment target independently and refuses fixture authentication even if a local profile is accidentally requested.

`GET /oauth2/authorization/google` starts production sign-in. `GET /login/oauth2/code/google` is the registered callback handled by Spring Security; validate issuer, audience, nonce/state, signature and expiry through the framework. First sign-in creates the account from validated issuer/subject. Any Google user may sign in; private lists still require membership. Do not use Google email as an account key or expose provider tokens to React. OAuth cancellation/failure returns a public error page without granting access. In local mode these provider routes are unused; application session state identifies the one fixture user.

`GET /api/session` returns authenticated flag and, if signed in, `{userId, displayName, email, authMode}`. `GET /api/csrf` obtains CSRF state for browser writes. `POST /api/logout` invalidates the application session. Cookie HttpOnly, SameSite=Lax, Secure in production; secure flag relaxed only for HTTP loopback local mode. Sessions expire after seven days idle or process restart. Google account recovery stays with Google.

All mutations require CSRF protection; nonmembers receive 404 for inaccessible list resources, unauthenticated calls 401, authenticated members lacking owner rights 403. Invalid input 422, version conflicts 409, unknown resources 404. No mutation uses GET. Session and authorization checks also apply to recurring-setting endpoints and archive access.

## Public content routes

These are the rewritten application's contracts. There is no obligation to retain previous response fields, endpoint aliases, or page URLs.

- `GET /api/recipes`: catalogue entries `{id, title, url}` with stable slug IDs and content revision; no user schedule configuration or dated assignments are exposed in this public response.
- `GET /api/recipes/{recipeId}?measurementSystem=metric|us`: full recipe projection, categorized ingredients, sections and typed segments; resolved explanation controls bundled from the same content snapshot: visible label, effective explanation, and optional glossary ID. Per-occurrence explanation overrides take precedence; standalone annotations need no glossary ID. Canonical stored amounts accompany converted display amounts. Every recipe, including method recipes, uses this one detail endpoint.
- `GET /api/ingredients`: canonical ingredient catalogue, aliases, categories, and supported unit metadata.
- `GET /api/glossary`: canonical term catalogue and revision; reusable for inspection, not required as a separate request for each bubble.
- `GET /health`: readiness (valid content, database available, migrations complete), safe status and running commit; do not expose config/secrets.

React routes `/` (recipe directory), `/recipes/{recipeId}`, `/schedules`, `/schedules/{scheduleId}`, and `/lists` use content identifiers and shared components. Schedule routes require authentication and show the user’s configured schedules, with dated meals including today and rest-day states. Recipes remain public. There is no implicitly selected global schedule. Previous/next follows the fifteen rotation recipes, wraps, and excludes method-only recipes from rotation order. There is one recipe route, with no old numeric-route alias, static menu page, or previous-page redirect layer. Deep-link fallback must not turn unknown API/assets into a successful HTML response.

## Authenticated schedule routes

- `GET /api/schedule-templates`: available templates for signup, initially the sole supplied three-week template; authenticated users only.
- `GET /api/schedule-templates/{templateId}`: authenticated template metadata and relative recipe/rest-day slots, with no shared absolute anchor.
- `POST /api/user-schedules`: `{templateId, anchorDate}` accepts any calendar weekday as cycle day 1 and creates a personal configured schedule for the signed-in user, independent of shopping lists.
- `GET /api/user-schedules`: only the signed-in user’s configured schedules.
- `GET /api/user-schedules/{userScheduleId}`: owner-only personal configuration, including linked-list summaries so the deletion UI can name affected lists and explain that automatic additions will stop.
- `DELETE /api/user-schedules/{userScheduleId}`: owner-only, CSRF-protected and version-preconditioned deletion after explicit UI confirmation; detach all linked lists and stop their additions, preserve existing entries and origin, return 204. Direct calls from other users are denied.
- `GET /api/lists/{id}/schedule/meals?from=YYYY-MM-DD&to=YYYY-MM-DD&timeZone=...`: member-only dated view of the currently linked effective schedule, without exposing another user’s private catalogue. Detached lists return no active schedule.
- `PATCH /api/user-schedules/{userScheduleId}`: owner-only `{anchorDate}` with a version precondition; update personal dated meals and future generation of linked list attachments, clearing each linked coverage marker only when the anchor actually changes; retain historical entries, completed-occurrence identities and list-specific recurrence.
- `GET /api/user-schedules/{userScheduleId}/meals?from=YYYY-MM-DD&to=YYYY-MM-DD&timeZone=...`: owner-only dated meals for a half-open range, derived from the selected template and user anchor. A today view requests one local day. Dates before the personal anchor return `{date, status: not_started}` without a recipe or cycle position; later dates return meal/rest-day status.

Unauthenticated schedule requests return 401; another user’s personal configuration returns 404. Shared-list members access their list's effective imported schedule through the authorized list endpoints, not another user’s private configuration routes. Import is a live link to the personal configuration. Anchor dates may be any weekday and shift all recipe/rest slots together. Dates before the anchor have not_started status and contribute no shopping ingredients. Source deletion detaches linked lists and stops future generation while retaining existing purchases.

## Viewer-local date presentation

The browser reads its current local IANA zone and sends it in personal/list dated-view and preview requests. Use it to choose today, default browse ranges, the one-month future horizon, and display of actual timestamps such as next addition time. Refresh on local-date/zone changes or when returning to a visible view; include viewer zone in cache keys. A schedule owner sees their own local today; another authorized list member sees theirs.

Explicit `from`, `to`, anchor, and meal dates remain date-only labels. Do not convert a meal date from the owner’s midnight to the viewer’s zone. The same requested date has the same recipe for everyone. Display actual next-run instants in viewer local time with a zone label; keep the configured addition zone visible in owner settings. The actual batch coverage/ingredients still come from the attachment’s configured addition zone, and a viewer-zone change never rewrites them or resets coverage markers. A generation preview preserves its actual covered meal-date labels even though its display horizon is viewer-local.

## Date-range request bounds

All user-facing personal/list meal views use a half-open `[from, to)` range with `from < to`, `to <= from.plusMonths(1)`, and `to.minusDays(1) <= today.plusMonths(1)`. “Month” means calendar-month arithmetic with end-of-month clamping, not a fixed 30 days. Invalid or excessive explicit ranges return 422 with a clear range error before evaluating recipes. Past browsing is allowed within the same one-month per-request span. Compute today in the requesting viewer’s current browser/device IANA time zone, supplied as required `timeZone`. Reject missing/invalid zones with 422; the client uses an explicitly labeled UTC fallback only when detection is unavailable. Never substitute the owner’s zone, the list addition zone, or the server zone.

Automatically derived list previews clip dates and ingredient totals to the permitted one-month window and return both the full candidate generation-period metadata and the displayed range with `isPartial`. If the next period starts beyond the horizon, return no dated preview with a clear beyond-horizon state. Never label partial totals as the entire batch. Internal scheduled generation and trusted operator CLI previews retain their configured coverage; anchor configuration itself may lie farther in the future.

## Private collaboration routes

| Method and path | Input / effect |
| --- | --- |
| GET /api/lists | Member's catalogue only, with owner flag and summary |
| POST /api/lists | `{name}`; create private list and owner membership with no attached schedule or automatic generation, return 201 |
| GET /api/lists/{id} | Name, owner/current membership, version, age limit, current schedule outcome/next due |
| DELETE /api/lists/{id} | Owner-only deletion with version precondition; end access and invalidate links/jobs, return 204 |
| DELETE /api/lists/{id}/membership | Nonowner leaves, return 204; owner receives ownerCannotLeave |
| POST /api/lists/{id}/invitations | Owner-only; return link and expiresAt, token only on creation |
| DELETE /api/lists/{id}/invitations/{inviteId} | Owner-only withdrawal, return 204 |
| POST /api/invitations/accept | `{token}`; authenticated explicit acceptance; idempotent membership |
| GET /api/lists/{id}/entries | `state=active|archived`, `sort=alphabetical|chronological`, `groupBought=true|false`; return entries with versions |
| POST /api/lists/{id}/entries | `{name, ingredientId?, quantity?, unit?}`; independent unchecked manual entry, return 201 |
| PATCH /api/lists/{id}/entries/{entryId} | `{bought: boolean}` and version precondition; active members can set bought state |
| DELETE /api/lists/{id}/entries/{entryId} | Remove active entry or clear one archived entry, with version precondition |
| DELETE /api/lists/{id}/archive | Clear all entries already archived when transaction executes; preserve active entries |
| PUT /api/lists/{id}/archive-policy | Owner-only `{ageLimit:{count, unit:day|week}}` plus list version |
| PUT /api/lists/{id}/schedule | Owner live-links `{userScheduleId}` from their own configurations, with separately configurable list addition recurrence/time zone; default weekly Saturday 09:00 America/Toronto. New imports create one immediate initial batch up to the next configured addition date; existing-attachment settings edits do not create another initial batch and preserve the coverage marker. Candidate batches omit previously covered dates for the unchanged source anchor. No implicit attachment on list creation; version precondition and import Idempotency-Key |
| DELETE /api/lists/{id}/schedule | Owner-only detach future generation; retain attachment history, existing entries and purchase origin |
| GET /api/lists/{id}/schedule/preview?timeZone=... | Dated meals and aggregate purchases for the next due occurrence's exact coverage interval |
| POST /api/lists/{id}/schedule/retry | Owner-only failed occurrence ID; attempt the same occurrence, no fresh immediate batch |

If-Match carries the expected quoted entity version on versioned mutations. Creates accept an Idempotency-Key; the same key/payload returns the same result during the seven-day retry window, while another key represents a deliberate addition even for identical content. List sorting uses canonical display name and deterministic createdAt/ID tie-breakers; bought grouping places unchecked first without changing items. No raw timestamp need be displayed in the UI.

Invite links use a public `/join#token=...` route to keep the secret out of normal request paths/referrers. The frontend retains the token through sign-in only in same-origin session storage, clears the fragment, and explicitly posts acceptance after authentication. Do not auto-accept on GET or reveal private list contents before acceptance. Responses distinguish unusable invitation from success without exposing list details to unauthenticated users.

## Automatic updates and expiry

Use a lightweight two-second polling interval for connected, visible list views, refreshing catalogue/list/entries with bounded nonoverlapping requests. Stop polling while invisible; on becoming visible request fresh state immediately and resume polling. The five-second visibility guarantee applies only while visible and connected. Refresh immediately after a local mutation, window focus, or reconnection. Stop polling inaccessible/deleted lists and remove them from catalogue state. Show a disconnected/outdated indicator on failure; avoid queuing offline edits in this prototype. Verify five-second visibility under the specified workload; revisit transport only if polling cannot meet it. No WebSocket/SSE infrastructure is required initially.

Read transactions apply age eligibility per item before returning active totals. A small backend worker may materialize eligible archival transitions, but there is no list-wide archive timer. The same backend processes due additions; no Telegram or separate job daemon.

Coverage markers are maintained by successful generation, never accepted as client-writable fields. Preview reports only currently uncovered dates within its displayed range, but does not advance the marker. Anchor changes clear markers through the owner-only personal-schedule update transaction; they do not rerun completed occurrence IDs or remove prior checklist entries.

## Operator commands

Provide Java entry points used by scripts and CI:

- `validate-content`: validate all YAML content, quantities, assets, and references, including ingredient records.
- `preview-meals --template-id ID --anchor-date YYYY-MM-DD --start-date YYYY-MM-DD --days N`: preview an explicit positive number of local calendar dates, including rest days.
- `preview-shopping --template-id ID --anchor-date YYYY-MM-DD --start-date YYYY-MM-DD --days N`: aggregate purchases for that explicit date range without mutating lists.

Commands run through the packaged Java application with trusted local operator access and an explicit template/anchor, not a public schedule endpoint. They use the new content format. Success exits 0; invalid input/content or runtime failure exits nonzero with a concise diagnostic. No compatibility shell, old command names/flags, or previous numeric error-code distinctions are required. CI validation needs neither Google credentials nor a production database. The rewritten runtime does not require Python.
