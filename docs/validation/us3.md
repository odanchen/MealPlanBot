# User story 3 verification

Implemented T044–T053 on 2026-10-18. The requirements checklist remains unchanged
(16 checked, zero unchecked). No `.specify/extensions.yml` exists, so no pre/post
implementation hooks apply.

## Delivered behavior

Owners create multi-use invitations and withdraw them. Links contain a random
256-bit URL-fragment token; SQLite stores only its SHA-256 hash. Expiry is exactly
72 hours after creation. Only creation returns the plaintext link; owner metadata
reads exclude both plaintext tokens and hashes. Acceptance requires an authenticated,
CSRF-protected POST. Expired, withdrawn and deleted-list links grant no access.
Repeated acceptance inserts no duplicate membership.

Members can collaborate on purchases, view/clear archived entries and leave a list.
Their contributions remain after leaving. Owners can delete the list for everyone
and cannot leave it; other members cannot delete it. Version-preconditioned deletion
cascades memberships, invitations and entries. Every competing mutation/acceptance
rechecks list access/existence inside its SQLite transaction.

The join page clears the fragment before session bootstrap and retains the pending
token in same-origin session storage through sign-in navigation. It shows no list
contents before explicit acceptance. Owner sharing controls display the new link,
expiry metadata and withdrawal; member controls expose leave instead of delete.
Archive clearing uses shared confirmation dialogs and membership-checked APIs.

Catalogue, detail and archive views use two-second polling with one request group
at a time. Concurrent triggers coalesce into a trailing refresh. Focus, visibility
restoration, reconnect and saved actions refresh immediately. Hidden/offline views
stop polling; unavailable requests time out after eight seconds. Failed refreshes
show an outdated/disconnected state and disable writes. Lost access clears private
view data and stops further polling. Offline writes are never queued.

## Verification

`./scripts/check.sh`: PASS, including locked npm installation, lint, typecheck,
frontend tests, backend tests/content validation, executable JAR build and Chromium.

- Backend: 48 tests pass against actual SQLite. New tests cover multi-use/idempotent
  acceptance, exact 72-hour expiry (valid one millisecond before, invalid at the
  boundary), withdrawal, nonmember privacy, owner-only controls, immediate loss of
  access after leaving, preserved contributions and archived single/all clearing.
- Concurrent different-entry updates both persist; stale same-entry updates conflict.
  Five concurrent deletion/acceptance/item-mutation races allow only results valid
  before or after deletion. Afterwards, no memberships, invitations or entries remain,
  and the invitation cannot recreate access.
- Frontend: ten tests pass, including controlled slow-request checks for nonoverlap,
  coalesced refreshes, teardown and permanent polling shutdown after access loss.
- Chromium: all six journeys pass. The new sharing journey uses independent real
  backend principals for owner, member and outsider, with disposable SQLite. It
  verifies explicit acceptance, contribution updates, bought state, archive clearing,
  leave/rejoin, owner deletion and subsequent unusable invitations.
- Both another member's addition and bought-state change appear within the asserted
  five-second limit. A hidden member view issues zero polling requests across a
  2400-millisecond interval; visibility restoration fetches the new item immediately
  and renders within five seconds. Offline controls disable writes, and reconnection
  fetches current purchases. After deletion is observed, the member issues zero list
  requests across a further 2300-millisecond interval.
- A second browser journey verifies anonymous sign-in return to `/join`, fragment
  removal with the pending token retained, withdrawn links and expiry after a
  controlled 72-hour advance.
- The existing private-shopping browser journey still passes its 320-pixel axe scan,
  no-overflow check, confirmation focus checks and refresh/sign-out persistence.
- The normal application ignores browser fixture identity headers and has no fixture
  control endpoints. Inspection of the executable JAR finds no browser test,
  controllable-clock or test-support classes. The browser launcher uses an explicitly
  separate test classpath and loopback/local-only fixtures; normal local operation
  retains one fixture account and exposes no user selector.
- `git diff --check` passes. Dependency locks, verification metadata, source content,
  checklist markers and production state remain unchanged.

## Limits

Real Google sign-in, Pi resource/latency measurements and the ten-member/500-item
load acceptance remain later operational work. Browser timing verifies these local
acceptance journeys, not a production workload percentile. Schedule attachment,
automated generation and age-based archival remain US4; archive tests here seed
already archived entries through test-only fixtures. No production deployment ran.
