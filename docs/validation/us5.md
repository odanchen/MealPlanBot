# User story 5 verification

Implemented T076–T082 on 2026-10-18. Requirements checklist remains 16 checked,
zero unchecked; no extension hooks are configured.

One ExplanationResolver validates and resolves glossary-backed annotations, local
label/explanation overrides and standalone controls. Explicit term IDs must exist
even when overridden; standalone controls require both fields. Mixed shapes,
blank/null overrides and invalid references are rejected. Resolving a recipe never
mutates the shared glossary or another occurrence.

The shared CookingTerm control opens on hover/focus, pins on click/touch, dismisses
on Escape/outside interaction, and does not immediately reopen on retained focus.
Only one bubble is open. A short pointer handover delay lets users enter the bubble;
pinned bubbles survive pointer movement. Fixed positioning prefers above, flips or
shifts within the viewport and constrains width at 320 pixels. The tooltip is inside
the main landmark, has plain noninteractive text, and is associated by aria-describedby
with the keyboard trigger. InlineContent renders all explanation forms identically,
keeps unannotated text plain and uses ordinary section-linked recipe anchors.

Tutorial jargon is explicitly annotated, including cutting, sauce, rice and biryani
terms. A stew occurrence has a local braising explanation; a grain annotation is
standalone. The extracted Simple rice method is the sixteenth public recipe but is
not in the fifteen-recipe rotation. Two parent rice steps link to its method section;
their original visible wording and purchasing ingredients remain intact. A back
control returns from the method to the referring recipe.

Verification:

- Full `./scripts/check.sh` passes: 64 backend tests, 12 frontend tests, nine browser
  journeys, content validation, lint/typecheck and executable JAR packaging.
- Resolver tests verify shared-definition update propagation, override/standalone
  isolation, inflected labels, invalid shapes and missing explicit term references.
- The original content-only baseline still verifies all fifteen titles, servings,
  preparation guidance, ingredient quantities and all 71 ordered instruction texts.
  No baseline expectations were rewritten; one new method recipe is counted separately.
- Removing the instruction-only method from the planner's content projection does
  not change any of the three-week purchasing totals; links never expand ingredients.
- Component cases cover hover, pin, retained-focus dismissal, pointer handover,
  one-open-bubble behavior and outside dismissal.
- Chromium covers keyboard and touch interaction, override text, method navigation
  and return, 320-pixel tooltip bounds and a passing axe scan. The mobile screenshot
  was visually inspected. All existing shopping/schedule/sharing journeys still pass.
- Final additional biryani/pasta annotations pass focused content tests and validation,
  including the unchanged tutorial comparison baseline.

No production deployment, dependency updates or checklist changes were made.
