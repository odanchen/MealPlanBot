# Review of the supplied recipe content

This review records editorial work on the fifteen supplied tutorials. Their complete
text and purchasing quantities are captured in the Java content-only baseline.
Each migrated tutorial now has one authoritative `recipe.yaml`; the former HTML,
JSON and manifest copies have been removed after migration comparison passed.
The historical navigation details below describe the original review. The new
application uses stable slugs, shared rendering and wraparound recipe navigation.

## Corrections and editorial choices

- Replaced all placeholder manifest filenames and titles with the supplied files
  and exact dish headings. Updated the static menu with the same titles.
- Verified Sunday–Thursday labels across all three weeks. The “Day 1–15” labels
  on pages refer to cooking slots, whereas API `cycle_day` spans 1–21 calendar days.
- Fixed duplicate “servings servings” text on all pages. Servings and cooking
  times remain author estimates, not independently kitchen-tested results.
- Kept Today, Previous and Next as normal local links. The brand link now opens
  the full menu. The first/last recipe has only its available neighbour; the
  schedule itself still wraps after three weeks. Added visible keyboard focus.
- Used the supplied JSON quantities as the ingredient quantities displayed in
  each tutorial. This selects the supplied amounts where the original HTML used
  ranges, cups, spoonfuls or “to taste”; it does not calculate densities. Pantry
  seasoning is added gradually to taste. Optional items already in JSON remain
  on the purchase list, and optional extras outside JSON are identified clearly.
- Recipes 2, 7 and 14 now explicitly require advance soaking/cooking of the
  supplied 200 g dry pulses and use that drained batch. Removed the unverified
  “one cup dry equals three cups cooked” implication. Cooking-time estimates
  exclude that advance work; no bean yield is inferred by the application.
- Kept the supplied tomato weights in recipes 9 and 11 instead of implying
  that two or three tomatoes must weigh exactly 200 or 300 g.
- Clarified recipe 6's rosemary purchase as three **sprigs**, recipe 9's star anise
  purchase as one **whole star** (use one point and save the rest), and recipe
  12's 900 g squash as **raw peeled flesh**. No whole-squash yield was guessed.
- Clarified shared lemon juice, oil and salt quantities so a measured ingredient
  can be divided between components without silently requiring a second amount.
- Removed a leftover flour alternative from the casserole method because the
  supplied purchase record chooses cornstarch.

## Cooking guidance checked

For this Toronto household project, cooking-temperature language follows
[Health Canada's safe cooking temperatures](https://www.canada.ca/en/health-canada/services/general-food-safety-tips/safe-internal-cooking-temperatures.html).
Pork medallions now specify 71°C; the beef stir-fry gives an explicit whole-cut
minimum instead of undefined “safe doneness.” Existing 74°C poultry and shrimp
and 71°C ground-beef instructions are consistent with that reference.

The biryani now calls for firm egg whites and yolks rather than promising that
8½ minutes will give the same result with every egg size and starting temperature,
following [Health Canada's egg guidance](https://www.canada.ca/en/health-canada/services/meat-poultry-fish-seafood-safety/eggs.html).
These are editorial and consistency checks, not evidence that the recipes have
been cooked or that their taste, serving yields and elapsed times are guaranteed.

## Migration verification

`ContentMigrationTest` compares all fifteen recipes for titles, ingredient identities,
exact quantities and purchase units, serving estimates, timing, preparation guidance
and all 71 ordered steps. It also verifies all 21 relative meal/rest slots.
`ContentValidationTest` checks atomic revision replacement and invalid source rejection.
`./scripts/check.sh` runs these checks and the credential-free Java content validator.
