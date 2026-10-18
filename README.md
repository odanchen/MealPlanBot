# Meal planner

A household meal planner built with Java 25, React and SQLite. Browse public recipes,
create private personal meal schedules, share shopping lists by invitation, and generate
purchases from a linked schedule. Lists support independent item expiry, archive,
bought grouping and sorting. Recipes share cooking explanations and measurement controls.

## Run locally

Use JDK 25 and Node 24.22.0 (`nvm use`). The committed Gradle 9.8.1 wrapper needs
no global Gradle or database server.

```sh
npm ci
npm ci --prefix frontend
(cd frontend && npx playwright install chromium)
./scripts/check.sh
./scripts/build.sh --allow-dirty
./scripts/run-local.sh
```

Open http://127.0.0.1:8000. Local mode uses one fixture identity and ignored
`.local/mealplan/app.sqlite`; it requires no Google credentials. The executable JAR
is `backend/build/libs/mealplan-0.1.0.jar`. Local dirty bundles are marked and rejected
by production verification. A clean `./scripts/build.sh` produces a promotable bundle.

See [development](docs/development.md), [deployment and recovery](docs/deployment.md),
and [current acceptance evidence](docs/validation/acceptance.md). Format with
`npm run format`; `scripts/check.sh` enforces the shared formatter and runs backend,
SQLite, content, frontend and Chromium checks.

## Content and schedule rules

Tutorials live in `content/recipes/<stable-slug>/recipe.yaml`; shared ingredients,
aliases and explanations live in `content/ingredients.yaml` and `content/glossary.yaml`.
Fifteen migrated tutorials and one reusable rice method use the shared renderer.
`content/schedules/three-week.yaml` contains the relative 21-day rotation. No numeric
recipe identities or old CLI/API aliases are provided.

```sh
./backend/gradlew -p backend validateContent
java -jar backend/build/libs/mealplan-0.1.0.jar validate-content --content-path content
```

A validated whole content revision is accepted on startup. Restart local development
after authoring changes; deploy a new release for production changes. The source-content
comparison baseline preserves the original methods, quantities and preparation guidance.
See the [content contract](specs/001-upgrade-recipe-planner/contracts/content-format.md).

A personal schedule starts on any chosen date. Earlier dates have no meal. A new list
has no attached schedule. Import creates a live link and one initial batch; future
source-anchor changes affect future generation, retain old purchases, and reset linked
coverage markers. Addition settings belong to each list. Deleting a personal schedule
warns about currently linked lists, detaches them, and preserves their entries and origins.
Viewer zones affect today and display bounds; generation uses the configured addition zone.

## Delivery status

CI checks pull requests without production secrets and publishes commit-keyed bundles
only after successful trusted-main checks. The Pi runs the downloaded JAR and validated
content through systemd. Deployment backs up and migrates stopped application state and
restores the matching database/application/content on failure.

The Python and Telegram runtime is retired from this checkout. No installed service
or production state has been changed by this implementation. Actual Pi performance,
PrivateTmp/native loading, live Google sign-in, target cutover and ten-person acceptance
remain pending; local checks do not establish those results. Production uses Google
OIDC and a canonical HTTPS origin; credentials belong outside Git.
