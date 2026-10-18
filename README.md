# Meal planner

A household meal planner built with Java 25, Spring Boot, React and SQLite. Setup and Foundation (T001–T017) are implemented; recipe migration, schedules and shopping-list features remain in the backlog. The current UI provides the application shell and session handling.

## Run locally

Use JDK 25 and Node 24.22.0 (`nvm use`). The repository includes the pinned Gradle wrapper; no global Gradle or database server is needed.

```sh
npm ci --prefix frontend
cd frontend
npx playwright install chromium
cd ..
./scripts/check.sh
./scripts/build.sh
./scripts/run-local.sh
```

Open http://127.0.0.1:8000. Local mode authenticates one stable fixture user and stores state in ignored `.local/mealplan/app.sqlite`. Local authentication requires no Google credentials or network access. The executable JAR is `backend/build/libs/mealplan-0.1.0.jar`.

See [local development](docs/development.md) for dependency updates and [Foundation verification](docs/validation/foundation.md) for the verified behavior and remaining limits.

## Recipe sources

The fifteen original tutorials and purchase records remain under `content/recipes/`. Their manifest and static assets are retained as migration inputs; the Java application does not serve them yet. Preserve them until the content migration and comparison checks are complete. [Content review](docs/content-review.md) records editorial decisions, and [purchase records](docs/ingredients.md) documents the original data format.

## Production status

The legacy Python runtime, package dependencies, tests and Python systemd templates have been removed from this checkout. No installed service or production state has been changed. Java deployment, backups, Pi measurements and real Google sign-in acceptance remain pending; this foundation is not a production cutover.

The backend supports Google-only OIDC outside local mode, with a canonical HTTPS origin and backend-owned sessions. `.env.example` lists the production environment variables; the Java application does not automatically load `.env` files. Production credentials stay outside Git. Deployment/service templates will be added by the operational tasks after the application features are verified.
