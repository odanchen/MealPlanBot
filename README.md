# Meal planner

A household meal planner built with Java 25, Spring Boot, React and SQLite. Setup, Foundation and User Story 1 (T001–T033) are implemented: browse fifteen public recipes and sign in to create private personal schedules with any-date anchors. Shopping-list features remain in the backlog.

## Run locally

Use JDK 25 and Node 24.22.0 (`nvm use`). The repository includes the pinned Gradle wrapper; no global Gradle or database server is needed.

```sh
npm ci
npm ci --prefix frontend
cd frontend
npx playwright install chromium
cd ..
./scripts/check.sh
./scripts/build.sh
./scripts/run-local.sh
```

Open http://127.0.0.1:8000. Local mode authenticates one stable fixture user and stores state in ignored `.local/mealplan/app.sqlite`. Local authentication requires no Google credentials or network access. The executable JAR is `backend/build/libs/mealplan-0.1.0.jar`.

See [local development](docs/development.md) for dependency updates and [US1 verification](docs/validation/us1.md) for verified behavior and remaining limits.

Format the repository with `npm run format`; verify formatting without writing with
`npm run format:check`. The root lockfile pins Prettier and its Java/shell plugins.
`scripts/check.sh` enforces formatting before running application checks. See
[formatting setup](docs/development.md#formatting) for editor integration and exclusions.

## Recipe sources

Each tutorial lives in `content/recipes/<slug>/recipe.yaml`. Ingredient IDs, categories and aliases live in `content/ingredients.yaml`; definitions live in `content/glossary.yaml`. The relative 21-day rotation is `content/schedules/three-week.yaml`. The original tutorial content is preserved in the test baseline; superseded HTML/JSON sources were removed after comparison passed.

Validate a complete content revision without credentials or a database:

```sh
./backend/gradlew -p backend validateContent
# After building:
java -jar backend/build/libs/mealplan-0.1.0.jar validate-content --content-path content
```

The backend accepts a complete validated content snapshot on startup. Restart the local application after authoring changes. Invalid content fails validation without publishing a partial catalogue. See the [content contract](specs/001-upgrade-recipe-planner/contracts/content-format.md) and [editorial review](docs/content-review.md).

## Production status

The legacy Python runtime, package dependencies, tests and Python systemd templates have been removed from this checkout. No installed service or production state has been changed. Java deployment, backups, Pi measurements and real Google sign-in acceptance remain pending; this increment is not a production cutover.

The backend supports Google-only OIDC outside local mode, with a canonical HTTPS origin and backend-owned sessions. `.env.example` lists the production environment variables; the Java application does not automatically load `.env` files. Production credentials stay outside Git. Deployment/service templates will be added by the operational tasks after the application features are verified.
