# Feature Specification: Upgrade Recipe Hosting and Shared Meal Shopping

**Feature Branch**: `main` (existing branch; no branch-creation hook configured)

**Created**: 2026-10-17

**Last Updated**: 2026-10-18

**Status**: Draft — validated for planning

**Input**: Rewrite and upgrade MealPlanBot while preserving recipe hosting and the existing three-week meal rotation. Introduce authoritative structured recipes, cooking-term explanations, recipe references, measurement conversion, categorized ingredients, authenticated shared shopping lists, expiring invitations, recurring schedule additions, and configurable stale-item archiving.

## Clarifications

### Session 2026-10-18

- Q: Which meals should each automatic shopping-list addition cover? → A: Meals from the addition date up to, but excluding, the next scheduled addition date, using the configured time zone.
- Q: When another member changes a shopping list, should an already-open list update automatically? → A: Show other members’ saved changes automatically within five seconds while the list is visible and connected.
- Q: If someone manually adds an ingredient already on the list, should it remain a separate entry or combine with the existing quantity? → A: Keep each manual addition separate, even for the same ingredient, preserving its own creation time and bought state.

### Meal schedule review decisions

- Recipes are public; schedule templates, personal signup/configuration, and dated meals require authentication. Only the supplied template ships initially; there is no global calendar anchor.
- A personal configured schedule exists independently of shopping lists. Import into a list is a live link, with addition settings owned by that list.
- Any calendar date is cycle day 1. Dates before it show “not started” and generate nothing.
- Import adds one immediate initial batch through the day before the next regular addition date; recurrence then continues unchanged, defaulting to Saturday 09:00 America/Toronto.
- Deleting a personal configuration warns about linked lists, detaches all of them, and stops their additions while preserving existing items.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read consistent, complete recipes (Priority: P1)

A visitor browses the recipe directory and follows recipe links. An authenticated user signs up for an available meal schedule and chooses an anchor date to create a personal configured schedule. That configured schedule supplies dated meals and rest days; it is not implicitly selected by creating a shopping list. Every recipe provides consistent ingredients, ordered instructions, servings, preparation guidance, and optional images. A maintainer can update one authoritative recipe record without separately correcting each use of the recipe.

**Why this priority**: Recipe hosting is the application's core purpose and supplies the information used by every meal and shopping workflow.

**Independent Test**: Browse all fifteen migrated recipes and a complete 21-day cycle; update a recipe title and ingredient quantity, then verify every subsequent display and newly generated shopping preview uses the update.

**Acceptance Scenarios**:

1. **Given** the existing content, **When** migration is complete, **Then** all fifteen tutorials retain their ingredient quantities, methods, serving estimates, and preparation guidance, with no unexplained content loss.
2. **Given** a signed-in user with a configured schedule and chosen anchor date, **When** they view a date in that schedule, **Then** the meal or rest-day assignment is derived from its template and their anchor rather than a global application date. A visitor without authentication can browse recipes but cannot access schedule configuration or dated schedule views.
3. **Given** an authoritative recipe update, **When** a visitor next opens its page, directory entry, schedule entry, or generates ingredients, **Then** each uses the updated information without a separate content correction.
4. **Given** a recipe without an image, **When** it is opened, **Then** all recipe information remains readable without a broken-image placeholder.
5. **Given** the end of the rotation, **When** the visitor follows next, **Then** navigation returns to the first recipe; previous from the first returns to the last.

---

### User Story 2 - Maintain a private personal shopping list (Priority: P1)

A user registers, signs in, creates a named list, and manages purchases inside the application. The list appears in their catalogue and remains available after signing out and back in.

**Why this priority**: This establishes a complete application shopping workflow before sharing or automation is introduced.

**Independent Test**: Create an account and a list, add and remove items, toggle bought status, sign out and back in, and confirm the saved state and privacy using a second account.

**Acceptance Scenarios**:

1. **Given** a visitor, **When** registration and sign-in succeed, **Then** they can access their own list catalogue; signing out ends authenticated access to it.
2. **Given** an authenticated user, **When** they create a named shopping list, **Then** it is private, they become its owner, it appears in their catalogue, and no meal schedule is attached or automatic ingredient addition enabled.
3. **Given** an active list, **When** its member adds a named item with an optional quantity and unit, marks it bought or unbought, or removes it, **Then** the updated state persists.
4. **Given** another account without membership, **When** it attempts to discover, open, or change the list, **Then** access is denied and list contents are not revealed.
5. **Given** blank names or invalid quantities, **When** the user submits them, **Then** the application identifies the invalid input and preserves the existing list.
6. **Given** an ingredient already on the list, **When** a member manually adds that ingredient again, **Then** a separate unchecked entry is created with its own creation time; existing quantities, bought states, and archive eligibility remain unchanged.
7. **Given** a returning Google identity whose email has changed, **When** sign-in succeeds, **Then** the same internal user ID, owned lists, memberships, and prior contributions remain associated with that user.
8. **Given** a first-time visitor, **When** Google sign-in succeeds, **Then** their application account is created without selecting a local password; failed or cancelled sign-in grants no private access.

---

### User Story 3 - Share shopping with household members (Priority: P1)

An owner creates an invitation link. A recipient signs in or registers, accepts it, and sees the shared list in their catalogue. Every member can manage purchases and clear archived items. Members other than the creator can leave; the creator can instead delete the list for everyone.

**Why this priority**: Shared household shopping is a central requested capability and requires explicit privacy boundaries.

**Independent Test**: Use two accounts to accept an invitation and manage one shared list; test an unrelated account, expired invitation, concurrent changes, member departure, and owner deletion.

**Acceptance Scenarios**:

1. **Given** an owner-generated invitation, **When** an authenticated recipient explicitly accepts it before expiry, **Then** they become a member and the list appears in their catalogue.
2. **Given** a signed-out recipient, **When** they follow a valid link, **Then** authentication is required before acceptance or disclosure of list contents.
3. **Given** an invitation created 72 hours ago, **When** acceptance is attempted at or after its expiry, **Then** no membership is granted; a new invitation is required.
4. **Given** two members, **When** either adds, removes, or checks an item, **Then** both see the persisted result automatically within five seconds while their lists are visible and connected, without refreshing, recreating membership, or reopening the list.
5. **Given** concurrent edits to different items, **When** both complete, **Then** both changes are retained; conflicting edits to the same item produce a consistent final state and never resurrect a removed item silently.
6. **Given** an existing member, **When** they accept the link again, **Then** membership is unchanged and no duplicate catalogue entry appears.
7. **Given** a member who did not create the list, **When** they leave it, **Then** it disappears from their catalogue and they lose access, while the list, its entries, and other memberships remain intact.
8. **Given** the list creator, **When** they manage their membership, **Then** they can delete the list instead of leaving it; other members cannot delete it.
9. **Given** a shared list with active invitation links and scheduled work, **When** its creator deletes it, **Then** it disappears from every member's catalogue, all memberships end, all invitation links become unusable, and no further scheduled work changes or recreates the list.

---

### User Story 4 - Automatically prepare recurring shopping (Priority: P2)

A signed-in user selects the supplied schedule template and an anchor date, creating a personal configured schedule for daily meal viewing without requiring a shopping list. A list owner can then import their configured schedule into a shopping list and choose when ingredient additions occur in a named time zone. Each item moves to the archive when its own age reaches the list’s configured age limit, keeping newer items active independently of the addition schedule.

**Why this priority**: Automation removes repeated planning work once list management is dependable.

**Independent Test**: Use controlled dates covering all three rotation weeks to run additions and verify individual item expiry at different creation times, including retries, daylight-saving changes, and overlapping events.

**Acceptance Scenarios**:

1. **Given** a shopping list with an imported configured schedule and a configured occurrence, **When** its due time arrives, **Then** meals from that occurrence’s local date up to, but excluding, the next scheduled occurrence’s local date supply aggregated ingredients to the active list; rest days contribute none.
2. **Given** the same occurrence is processed again, **When** processing repeats, **Then** no duplicate additions or quantities appear.
3. **Given** an existing bought item from a previous occurrence, **When** another occurrence requires that ingredient, **Then** the new purchase need is unchecked and distinguishable from the earlier purchase.
4. **Given** a seven-day age limit and two items created two days apart, **When** the older item reaches seven days of age, **Then** only that item moves to the archive; the newer item remains active for two more days, regardless of bought status.
5. **Given** an item reaching its age limit at the same instant as new scheduled additions, **When** the list is viewed, **Then** that item is archived and the new items remain active until each reaches its own age limit.
6. **Given** a member viewing the archive, **When** they clear one item or all archived items, **Then** only the selected archived content is permanently removed.
7. **Given** incomplete recipe data for an occurrence, **When** generation is attempted, **Then** none of that occurrence's ingredients is added, the failure is visible to members, and retrying after correction can complete it once.
8. **Given** a changed time zone or disabled attachment, **When** future occurrences are evaluated, **Then** they follow the new configuration without altering previous items or replaying completed occurrences.
9. **Given** an owner changes the age limit, **When** the list is next viewed, **Then** active items are evaluated using their original creation times and the new limit; already archived items are not restored.
10. **Given** a signed-in user with no shopping list, **When** they sign up for the supplied template and choose an anchor date, **Then** their configured schedule is saved and they can view its daily meals.
11. **Given** an owner with a configured schedule and an unattached shopping list, **When** they import that configuration, **Then** list generation uses its effective template/anchor and defaults to weekly Saturday at 09:00 in the configured time zone.
12. **Given** an unauthenticated visitor or another user’s configured-schedule ID, **When** a personal schedule or dated view is requested, **Then** access is denied without revealing private configuration.
13. **Given** a personal configured schedule linked to a shopping list, **When** its owner changes the anchor, **Then** personal and list-specific future meal views and new generated batches use the updated anchor, all linked coverage markers reset, and existing entries, completed-occurrence identities, and the list’s addition frequency/time remain unchanged.
14. **Given** the supplied template anchored on a Monday, **When** its first seven days are viewed, **Then** Monday–Friday contain its first five recipes and Saturday/Sunday are rest days; the following Monday begins the second set of five recipes.
15. **Given** an active personal schedule imported on Tuesday with weekly Saturday additions, **When** import completes, **Then** one initial unchecked batch covers Tuesday through Friday; Saturday’s regular batch covers Saturday through the following Friday, without overlapping dates or shifting the recurrence.
16. **Given** the same import request is retried, **When** it completes again, **Then** neither the attachment nor its initial batch is duplicated.
17. **Given** an anchor later than the selected date, **When** the user views that date or a shopping batch covers it, **Then** the view shows “not started” and no ingredients are generated for that date. A batch spanning the anchor includes only its meals on or after the anchor; a batch wholly before it completes empty without moving the configured addition time.
18. **Given** a personal configured schedule linked to one or more shopping lists, **When** its owner requests deletion, **Then** the application warns which lists will lose automatic additions; on confirmed deletion the personal configuration is removed, every linked list is detached, future additions stop, and all existing active/archived entries remain unchanged.
19. **Given** personal-schedule deletion racing with generation, **When** deletion completes before a batch commits, **Then** that batch cannot add items or restore the link; batches committed before deletion retain their entries.
20. **Given** an unchanged anchor and a successful Saturday–Friday batch, **When** the addition day changes to Monday, **Then** Monday’s candidate Monday–Sunday interval adds only the previously uncovered Saturday/Sunday dates, excluding any rest days. Repeating the occurrence or removing prior items does not add the covered dates again.
21. **Given** a failed or wholly covered candidate batch, **When** generation completes or retries, **Then** failure leaves the coverage marker unchanged and success never moves it backward.
22. **Given** a dated-view request beyond one calendar month ahead or longer than one calendar month, **When** it is submitted, **Then** the request is rejected with a clear range error; an automatically derived list preview is visibly partial when its full generation period extends beyond the allowed query window.
23. **Given** two authorized members in different local time zones viewing the same linked schedule while their local calendar dates differ, **When** each opens the daily preview, **Then** each sees the meal for their own local today; selecting the same explicit calendar date yields the same recipe for both. Neither view changes the list’s next addition instant, generated quantities, or coverage marker.

---

### User Story 5 - Understand terms and reuse cooking methods (Priority: P2)

A cook reads concise instructions, reveals an unfamiliar highlighted term, or follows a reference to another recipe such as a generic rice method.

**Why this priority**: Optional explanations serve both experienced cooks and beginners without duplicating instructions.

**Independent Test**: Open a recipe with a cooking term and a recipe reference using a pointer, touch, and keyboard; inspect both explanations and referenced instructions.

**Acceptance Scenarios**:

1. **Given** an annotated term, **When** the user hovers, clicks, taps, or focuses it with a keyboard, **Then** its explanation appears in an associated bubble above it where space permits, without requiring navigation away.
2. **Given** an open explanation, **When** the user dismisses it or presses Escape, **Then** it closes; the explanation is accessible to assistive technology and remains available while the user interacts with the bubble.
3. **Given** a narrow screen or term at the top edge, **When** its explanation opens, **Then** the explanation stays readable within the viewport and does not hide the triggering term unnecessarily.
4. **Given** a recipe reference, **When** the user follows it, **Then** the correct referenced recipe or method opens and they can return to the original recipe.
5. **Given** an instruction-only reference to rice preparation, **When** a shopping list is generated, **Then** the parent recipe's listed ingredients are counted once; the linked recipe's ingredients are not automatically added again.
6. **Given** an annotation referencing a glossary term with a local explanation override, **When** its bubble opens, **Then** the local explanation appears while other uses retain the shared definition.
7. **Given** an annotation with a label and explanation but no glossary reference, **When** its bubble opens, **Then** the supplied explanation appears with the same interaction and accessibility behavior as a glossary-backed annotation.
8. **Given** an annotation with neither a glossary reference nor an explanation, an empty override, or no visible label when no term is provided, **When** content is validated, **Then** validation rejects it with a specific error.

---

### User Story 6 - Find and measure ingredients clearly (Priority: P2)

A cook switches measurement systems and scans ingredient categories. A shopper chooses alphabetical or chronological order and optionally separates bought from unbought items.

**Why this priority**: Clear quantities and predictable organization make everyday cooking and shopping easier.

**Independent Test**: Convert known weight and volume examples, aggregate plural aliases, inspect categories, and switch every shopping-list view without changing saved quantities or bought states.

**Acceptance Scenarios**:

1. **Given** a recipe, **When** the user switches between metric and US customary measurements, **Then** supported weights and volumes display equivalent amounts and a visible measurement-system label; counts and package descriptions remain meaningful.
2. **Given** one kilogram and 500 grams of the same ingredient, **When** a generated list is aggregated, **Then** the total equals 1.5 kilograms regardless of display system.
3. **Given** tomato and tomatoes mapped to the same ingredient, **When** compatible quantities are aggregated, **Then** they produce one ingredient total; distinct ingredients such as fresh tomato and tomato paste remain separate.
4. **Given** amounts with incompatible units and no established conversion, **When** aggregation or display conversion runs, **Then** those quantities remain separately labeled without a guessed equivalence.
5. **Given** categorized ingredients, **When** the recipe opens, **Then** ingredients are grouped under their assigned categories and none disappear for lacking a category.
6. **Given** a populated shopping list, **When** a member selects alphabetical or chronological ordering and toggles bought/unbought separation, **Then** all active items appear once with unchanged quantities and states in the selected order and grouping.

### Edge Cases

- An invalid or withdrawn invitation grants no access; possessing a list address alone grants no membership. An invitation is valid strictly before creation time plus 72 elapsed hours.
- Missing term definitions, broken recipe references, invalid quantities, and unrecognized units are reported by content validation before affected content is accepted. Mutually linked instruction pages do not trigger recursive ingredient expansion.
- Conversion never infers density, cooked-to-raw yield, count-to-weight equivalence, or package size. Optional ingredients present in the authoritative purchase ingredients are included; unquantified optional suggestions remain visibly optional and are not assigned invented quantities.
- An empty list, empty archive, or recipe without an image has a usable empty or image-free state. A method reference cannot remove ingredients required by the parent recipe.
- Retry or restart does not duplicate a completed scheduled occurrence. After downtime, only the latest missed addition occurrence is caught up, using that occurrence’s scheduled coverage dates; superseded occurrences are not added. After downtime, each item is evaluated against its own elapsed age; only items that have reached the age limit are archived. Catch-up additions start aging from their actual creation time.
- During daylight-saving transitions, a nonexistent local scheduled time runs at the first valid instant after the gap; a repeated local time runs once at its first occurrence. Invitation expiry still uses elapsed hours.
- On and after the selected anchor, cycle position is elapsed calendar days modulo the template cycle length, independent of weekday and year boundaries. The supplied template repeats five meal slots followed by two rest slots three times; anchoring on Monday therefore gives Saturday/Sunday rest days. Dates before the anchor have status “not started” with no assigned recipe or rest-day slot. All shopping generation, including the immediate import batch, MUST exclude dates before the anchor; an interval containing no eligible meals completes with no items. Date-range views require an authenticated user’s configured schedule identity or an authorized list context; automatic additions use that attachment’s schedule and the interval between scheduled addition dates. Creating a list never implicitly chooses the supplied schedule.
- An item remains active strictly before its creation time plus the configured age limit and becomes archived at or after that instant. Editing or checking it does not reset its age. Elapsed age is independent of time-zone or daylight-saving changes. Already archived or removed items stay out of later active-item updates.
- Unknown manual ingredient names remain usable as separate entries; they are never merged with a known ingredient on an uncertain spelling guess.
- Leaving a list preserves entries contributed by the departing member. They can rejoin only through a valid invitation. The creator cannot leave while retaining the list without an owner.
- List deletion overrides concurrent invitation acceptance, member changes, and scheduled work: once deletion completes, none can restore the list, grant access, or create new entries. Old list addresses and invitations grant no access.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Each recipe MUST have one authoritative structured record containing a stable identity, title, ingredient identities and quantities with units, ordered preparation and cooking instructions, serving estimate, preparation guidance, and available timing information. Records MUST support optional images with alternative text, term annotations, and recipe references.
- **FR-002**: Recipe pages, directory entries, meal schedules, new shopping generation, and read-only data access MUST derive recipe information from those authoritative records. Accepted changes MUST appear on the next read or generation without separate edits to duplicated content.
- **FR-003**: The rewrite MUST preserve all fifteen tutorials, the recipe directory, and recipe navigation. Meal planning MUST be driven by identified meal schedules rather than one hardcoded global rotation. Initially exactly one supplied schedule template contains the existing 21-day recipe/rest-day sequence. It MUST NOT contain an application-global calendar anchor. An authenticated user MUST be able to sign up for it and choose any calendar date as the anchor, making it day 1 of the cycle and creating a personal configured schedule independently of shopping lists. Recipe and rest-day positions MUST shift together with that date; no Sunday-only validation or fixed Friday/Saturday rest weekdays are allowed. Dates before the anchor MUST show “not started” and MUST NOT generate purchases. Dated meals, including today, MUST be evaluated within that personal configured schedule; a standalone global next-week feature is excluded. Personal daily views and shared-list meal previews MUST determine today from the current viewer’s local time zone, even when the viewer and schedule owner differ. Explicit anchor and meal dates MUST remain date-only values; selecting the same calendar date MUST select the same cycle slot for every viewer. User-facing date-range requests MUST be limited to at most one calendar month per request and MUST NOT return dated meals later than one calendar month ahead of today in the requesting viewer’s local time zone. This query limit MUST NOT prevent choosing a later anchor or shorten an internal generation period.
- **FR-004**: Content validation MUST identify missing required data, invalid quantities or units, missing term definitions, and unresolved recipe references; affected data MUST NOT be accepted as valid or generate a partial shopping occurrence.
- **FR-005**: Highlighted cooking terms MUST expose associated explanations through hover, click, touch, and keyboard focus, support dismissal, and remain readable within the viewport and available to assistive technology. Each annotation MUST support an optional glossary term reference, optional visible-label override, and optional explanation override. A nonblank explanation override MUST take precedence for that occurrence only, without changing the shared glossary. If no term is referenced, both a nonblank label and explanation MUST be supplied. If a term is explicitly referenced, it MUST resolve to an existing glossary entry. Without an explanation override, the shared definition MUST be used. Plain unannotated text MUST remain unchanged.
- **FR-006**: Instructions MUST support labeled links to other recipes and reusable method recipes. Following a reference MUST retain a usable return path. Instruction-only links MUST NOT implicitly contribute additional shopping ingredients.
- **FR-007**: Recipes MUST support metric and US customary ingredient displays using documented weight and volume equivalences and labeled unit conventions. Display changes MUST NOT modify authoritative quantities; displayed numerical conversions MUST differ from the exact equivalent by no more than 1% after rounding.
- **FR-008**: Ingredient identities MUST support singular/plural aliases, including tomato/tomatoes, without conflating different foods or product forms. Recipe ingredients MUST appear under assigned categories; initial categories MUST include spices, liquids, meats, vegetables, and an other/uncategorized fallback.
- **FR-009**: Within each generated shopping batch or preview, shopping generation MUST combine compatible quantities for the same ingredient identity before display rounding, retain incompatible quantities separately, and preserve explicit counts and package sizes. Manual additions and entries from different generated occurrences MUST remain separate. No density, preparation-yield, or package conversion may be inferred without established ingredient-specific information.
- **FR-010**: Users MUST be able to sign in through Google and sign out of the application. First successful sign-in MUST create their application account automatically. Any Google user MUST be permitted to sign in and create an application account; Google MUST be the only production authentication provider for the prototype; local run mode MUST use a single test user without Google sign-in; local passwords, password reset, and application-managed account recovery are excluded. Failed authentication MUST NOT grant private-list access, and saved lists and memberships MUST survive subsequent sign-ins. Google account recovery remains with Google.
- **FR-011**: An authenticated user MUST be able to create a nonblank named shopping list that defaults to private and makes them its owner. New lists MUST have no schedule attachment and MUST generate no automatic ingredient additions until a schedule is explicitly attached. Each user's catalogue MUST contain all and only lists where they hold membership.
- **FR-012**: Private list contents and mutations MUST be accessible only to authenticated members. Public recipe access MUST remain available without registration. Schedule discovery, signup/configuration, and dated schedule views MUST require authentication; personal configured schedules MUST be accessible only to their user. A shared list’s imported schedule and effective dates MUST be accessible to its members through the list, without granting access to another user’s personal schedule catalogue.
- **FR-013**: Owners MUST be able to create and withdraw invitation links. Each link MUST expire exactly 72 elapsed hours after creation, require explicit authenticated acceptance, and grant membership only to its associated list. Invalid, withdrawn, or expired links MUST grant no access.
- **FR-014**: Every list member MUST be able to add entries, remove entries, mark and unmark bought status, and clear individual or all archived entries. Adding an entry MUST require a nonblank name; any supplied numeric quantity MUST be positive and paired with a unit. Each manual addition MUST create a separate unchecked entry with its own creation time, even when the ingredient and unit match an existing entry; existing entries MUST NOT be merged or have their age or bought state changed by the addition.
- **FR-015**: Saved member changes MUST automatically become visible to other members within five seconds while their list is visible and connected, without a manual refresh. Concurrent changes to different items MUST be preserved; conflicting changes to the same item MUST resolve without silently restoring deleted content. Invisible views MUST NOT poll; when a view becomes visible it MUST immediately request the current saved state and resume visible-view polling, without a manual refresh. A disconnected view MUST indicate that its contents may be outdated and refresh to the current saved state on reconnection.
- **FR-016**: Members MUST be able to choose alphabetical ascending or chronological oldest-first ordering and independently enable unbought/bought sections. View changes MUST NOT alter underlying entries, quantities, or purchase states; showing timestamps is optional.
- **FR-017**: Owners MUST be able to import one of their personal configured schedules into a list, configure an addition recurrence and named time zone, and disable or change the attachment. The template catalogue MUST initially contain only the supplied schedule; personal configurations and imports MUST carry the selected template identity and user-chosen anchor rather than assume a global schedule. Import MUST create a live link to the configured user schedule. Changes to its anchor, including a change of weekday, MUST shift recipe/rest assignments together and affect subsequent dated views and future generation without modifying existing shopping entries; addition recurrence and time zone MUST remain list-specific and unchanged by those edits. An actual anchor-date change MUST clear the successfully covered-period marker on every linked attachment, allowing future batches to overlap dates generated for the previous anchor while preserving existing items and completed-occurrence retry protection. Defaults MUST be weekly on Saturday at 09:00 in America/Toronto, editable per attachment. Initial recurrence controls MUST support every N days or N weeks for a positive whole N, local time, and weekday for weekly recurrence.
- **FR-018**: Each addition occurrence MUST aggregate purchases for meals dated from that occurrence’s local date, inclusive, to the next scheduled occurrence’s local date, exclusive, in the attachment’s configured time zone. The coverage window MUST follow the selected recurrence, use the selected template and linked personal anchor, and exclude rest days and all dates before that anchor. Weekly recurring additions cover seven calendar days; recurring additions every two weeks cover fourteen. Import MUST also create one immediate initial batch covering the import’s local date, inclusive, up to the first subsequent configured addition’s local date, exclusive. The initial batch MUST NOT shift the regular recurrence. Each attachment MUST record its last successfully covered period with an exclusive end date. For the unchanged anchor, new batches MUST exclude dates before that recorded end, including after recurrence or time-zone changes; a wholly covered interval MUST add no items and MUST NOT move the marker backward. Generation, occurrence completion, and marker advancement MUST commit atomically; failure MUST NOT advance the marker. Changing a viewer’s display time zone MUST NOT change the list’s addition recurrence, generated coverage, saved items, or coverage marker. The marker tracks covered meal dates, not quantities bought or items still on the list, and MUST NOT reset when items are checked, removed, or archived.
- **FR-019**: Each recurring occurrence and the initial import batch MUST add its complete ingredient batch once, preserve its schedule and covered-meal origin, and keep new purchase needs unchecked. Aggregation MUST occur within the occurrence; prior entries from other occurrences or manual additions MUST NOT be silently merged or checked by it.
- **FR-020**: Owners MUST be able to configure a per-list item age limit as a positive whole number of days or weeks, independently of recipe additions. Each active item MUST move to the archive when its own elapsed age since creation reaches that limit, retaining its quantities, origin, and bought state. A list-wide recurring archive cutoff MUST NOT determine eligibility. A changed limit MUST apply to existing active items using their original creation times and MUST NOT restore archived items.
- **FR-021**: Members MUST be able to view archived entries and permanently clear one or all without affecting active entries. Archived items MUST be excluded from active views and totals. Active views and totals MUST exclude every item whose individual age has reached the limit, including after downtime. Newly added items MUST remain active until they individually reach the age limit.
- **FR-022**: Scheduled work MUST follow the daylight-saving and downtime rules in Edge Cases, prevent duplicate occurrences on retry, and show the latest addition outcome, covered meal dates, and next addition time and configured item age limit to members. A failed addition MUST preserve existing entries and permit retry after correction.
- **FR-023**: Previously materialized shopping entries MUST retain their saved quantities and origin when a recipe changes. Subsequent previews and scheduled occurrences MUST use the updated authoritative recipe; existing items MUST NOT be silently rewritten.
- **FR-024**: The application MUST provide read-only access to recipes and ingredients, schedule-specific dated meal views, plus operator content validation and schedule-specific meal/shopping previews, using authoritative content. These interfaces MUST use the new application’s identifiers and contracts; previous identifiers, URLs, payloads, command syntax, configuration aliases, and exact error/exit-code conventions are not compatibility requirements. Private application lists MUST require authenticated membership for access. Telegram integration, including commands and broadcasts, MUST be removed; user-facing shopping-list management and recurring additions MUST be provided within the application without depending on Telegram.
- **FR-025**: A member other than the list creator MUST be able to leave a shopping list. Leaving MUST end their membership and access and remove the list from their catalogue without deleting their contributions, changing other memberships, or deleting the list. Rejoining MUST require acceptance of a valid invitation.
- **FR-026**: The list creator MUST be its owner and MUST be able to delete the list instead of leaving it; other members MUST NOT be able to delete it. Deletion MUST remove the list from every member's catalogue, end all memberships, invalidate all invitation links, and disable all associated recurring additions and archiving. After deletion, direct access, pending invitation acceptance, member changes, and scheduled work MUST NOT expose or recreate the list or its contents.

- **FR-027**: Every user MUST receive an immutable application-assigned ID independent of email. Returning Google identities MUST resolve to that same user; changing their email MUST NOT create a new account or break ownership, memberships, or item attribution. Application-managed email changes and additional authentication providers are outside the prototype.
- **FR-028**: The application MUST support a local run mode with its own isolated data and configuration, requiring neither a Raspberry Pi, Cloudflare Tunnel, Google credentials, nor network access for authentication. Local authentication MUST be stubbed to one stable test user. Recipe, shopping, and scheduling behavior MUST match production; the stub MUST be unavailable in production, and production startup MUST reject attempts to enable it. Multiple-user authorization MUST remain covered by automated tests rather than adding a local user selector.
- **FR-029**: Each recipe MUST be supplied as one YAML content file within its own directory, with optional image files and without duplicated page layouts. The file MUST contain recipe metadata, ingredients, and ordered instructions, including explicit term and recipe references. Shared frontend components MUST render all recipe pages from that content. A shared glossary MUST supply default definitions across recipes; individual annotations MAY instead supply their own explanation, with or without a glossary reference.

- **FR-030**: A user MUST be able to delete their own configured schedule. Before confirmation, the application MUST identify linked shopping lists and warn that their future additions will stop. Confirmed deletion MUST remove the personal configuration and detach it from every linked list, without deleting lists, memberships, existing active/archived items, or their purchase origin. Generation that has not committed when deletion completes MUST NOT add items or recreate the configuration or links. Other users MUST NOT delete the personal configuration.

### Key Entities *(include if feature involves data)*

- **Recipe**: Stable identity, descriptive content, servings, timing, ordered instructions, ingredient amounts, optional images, term annotations, and labeled references. Method recipes use the same content model without requiring a rotation slot.
- **Ingredient**: Canonical identity, display name, singular/plural aliases, category, and any explicitly established ingredient-specific measurement information.
- **Recipe Ingredient**: Ingredient identity, authoritative amount and unit, relevant preparation notes, and optionality; contributes purchasing amounts once within its parent recipe.
- **Cooking Term**: Stable identity, canonical label, and one authoritative shared definition. Recipe annotations may reference that identity and supply a display form such as “sautéed”, an occurrence-specific explanation override, or both. Standalone annotations contain a visible label and explanation without a glossary identity. Only explicitly annotated occurrences are highlighted.
- **Meal Schedule Template**: Stable content identity, name, cycle length, and ordered recipe/rest-day slots, with no application-global calendar anchor. Initially only the existing 21-day rotation is supplied.
- **Configured User Schedule**: User-owned schedule signup, stable application ID, selected template identity, and user-chosen anchor date of any weekday, identifying cycle day 1. It supports daily meal viewing independently of any shopping list.
- **Imported List Schedule**: A shopping-list attachment sourced from a configured user schedule, carrying the effective template and anchor plus list-specific automatic-addition settings. Import is a live link: the attachment references the personal configuration and uses its current template and anchor for future generation; addition settings remain list-specific.
- **User Account**: Immutable application-assigned ID, external Google identity, mutable profile information including email, and list memberships. Email is not the primary identifier or the basis for linking accounts.
- **Shopping List and Membership**: Named private document, creator/owner, members, active entries, archive, and list-level addition recurrence and item age limit. Nonowners may end their membership; creator deletion ends the list and all memberships.
- **Invitation**: List association, invitation credential, creator, creation and expiry instants, and withdrawal state; acceptance creates membership without duplicating it.
- **Shopping Entry**: Item name and optional canonical ingredient identity, quantity/unit if supplied, creation time, bought state, active/archive state, and manual or scheduled origin.
- **Schedule Attachment and Occurrence**: List/schedule association, recurrence and time zone, due instant, covered meal dates, completion/failure state, and resulting entry batch.
- **Archive Policy**: Per-list elapsed-age limit, defaulting to seven days. Each entry becomes stale independently at its creation instant plus that limit; this policy has no list-wide archive recurrence or time zone.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All fifteen existing recipes pass a content comparison for ingredients, quantities, methods, serving estimates, and preparation guidance; all 21 relative cycle slots match the supplied template, including its rest days, and dated assignments use the chosen configured-schedule anchor rather than a global anchor.
- **SC-002**: For a representative update to a title, ingredient amount, and instruction, 100% of subsequent recipe displays and newly generated previews/occurrences reflect the accepted source; existing shopping quantities remain unchanged.
- **SC-003**: At least 90% of ten representative household users can register, create a named list, and add their first item within three minutes without assistance.
- **SC-004**: Across the membership and invitation acceptance tests, no nonmember can read or modify private list contents; acceptance succeeds before expiry and fails at or after 72 hours.
- **SC-005**: Across all three rotation weeks, repeated processing of an occurrence yields exactly one complete ingredient batch, invalid source data yields none, each coverage window contains every cooking date from its scheduled addition date up to, but excluding, the next addition date, and archiving moves each item only when its own age reaches the configured limit. In mixed-age boundary tests, 100% of younger items remain active while all items at or beyond the limit are archived.
- **SC-006**: All documented conversion examples meet the 1% display tolerance; singular/plural pairs aggregate correctly, while distinct products and incompatible quantities remain separate in every verification case.
- **SC-007**: All annotated terms and recipe references in the migrated content can be opened using keyboard and touch as well as pointer input; explanation text remains readable at a 320-pixel viewport width. All glossary-default, glossary-override, and standalone-explanation cases follow the specified precedence and validation rules.
- **SC-008**: Under a household workload of ten simultaneous members and 500 active entries per list, 95% of list reads and saved item actions become visible to the acting user within two seconds; another member's connected, visible view automatically reflects each completed change within five seconds.
- **SC-009**: At least nine of ten representative users can locate an ingredient, reveal a cooking-term explanation, check a purchase, and clear an archived item without assistance. Every ordering/grouping combination preserves 100% of the active entries and their states.
- **SC-010**: In all departure and deletion scenarios, departing nonowners lose access while remaining members retain the list; owner deletion removes the list from every catalogue and makes 100% of its prior invitation links unusable. Concurrent member actions and scheduled work never restore a deleted list.
- **SC-011**: All shopping-list creation, sharing, item management, recurring additions, and archive workflows can be completed within the application with no Telegram account, credentials, or service running; the rewritten application provides no Telegram commands or broadcasts.
- **SC-012**: Across personal-schedule lifecycle tests, all unauthenticated/other-user requests are denied, any-weekday anchors produce the specified relative cycle, pre-anchor dates generate zero ingredients, import creates exactly one initial batch, and deleting a source configuration detaches every linked list while preserving 100% of its existing active and archived entries.

## Assumptions

- This feature covers recipe-content migration and application behavior, not a public recipe editor or user-created schedule templates. Signing up for a supplied template with a personal anchor is included. Maintainers supply validated recipe, terminology, ingredient, and category content. Categories may evolve without changing ingredient identity.
- This is a complete rewrite with new content identities and interfaces. Recipe content, the supplied rotation, and requested user capabilities are retained; numerical recipe aliases, old page redirects, previous data-response shapes, and command wrappers are outside scope. Read-only access and validation/preview capabilities are designed for the new application. Telegram integration is dropped entirely; the application owns all user-facing shopping-list capabilities. Saturday recurring additions replace the shopping preparation workflow without requiring a messaging broadcast.
- Recipes remain public. Personal configured schedules and personal/shared shopping lists require Google sign-in; any Google user may sign in, and first sign-in creates an application account. There is no application account allowlist; shopping-list membership remains required for private access. Production uses only Google; local runs use one stable stub test user. The prototype has no local password or account-recovery system. Each user receives an immutable application ID; an email change on the same Google identity must preserve that account and all its memberships.
- The creator remains the owner and controls invitations, addition recurrence, item age limit, and list deletion; all members have equal item and archive permissions. Nonowners can leave without affecting the remaining household. Links may be accepted by multiple authenticated recipients before expiry. Removing other members, ownership transfer, and public list discovery are outside this feature.
- The default addition recurrence is Saturday at 09:00 in America/Toronto, covering that Saturday through the following Friday; rest days contribute no ingredients. Import creates an immediate initial batch covering the remaining dates before the first subsequent configured addition; regular additions then follow the chosen recurrence. Each new list has zero attachments; it initially supports at most one explicitly selected schedule attachment. Schedule-template creation is deferred. Authenticated users sign up for the supplied template with personal anchor dates before importing a configured schedule into a list.
- The default item age limit is seven elapsed days from each item’s creation, whether added manually or by a schedule. A day is 24 elapsed hours and a week is seven such days. The limit applies equally to bought and unbought items, including lists without a recipe schedule. No list-wide archive schedule, automatic archive expiry, or restoration is included.
- Recipe updates change the source for future shopping generation. Materialized shopping entries are historical purchase intentions rather than additional editable recipe sources. This preserves member actions and avoids unexpectedly changing a household's current purchases.
- Recipe references are instructional links. Parent recipes explicitly include all their purchasing ingredients; automatic inclusion or scaling of linked recipes is outside this feature.
- Metric is the default measurement display; US customary uses explicitly labeled US cups, tablespoons, teaspoons, ounces, and pounds. Ingredient purchasing counts and package descriptions remain explicit, and cooking-temperature conversion is outside this ingredient-focused requirement.
- Ingredient aliases are curated rather than inferred from arbitrary wording. Manual entries may use a known ingredient identity or remain free text. Each manual addition remains separate regardless of ingredient identity or compatible units; aggregation applies only within a generated batch or preview. Recipe quantities and original serving estimates are preserved; serving-size scaling and pantry-stock deductions are outside this feature.
- Local execution uses the same SQLite persistence behavior as Pi deployment, but separate data and one stable test identity. Google authentication is exercised through automated identity-mapping tests and a production-profile smoke test rather than required for local startup.
- Reliable dates, named time-zone rules, preserved recipe source content, a maintained ingredient/term catalogue, and durable user/list state are dependencies. The performance target is household use rather than a large public marketplace.

