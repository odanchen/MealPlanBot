# Local development

The Java/React foundation is the only application runtime in this checkout. The legacy Python implementation has been removed; no installed production services have been changed. All six user stories are implemented, including recurring shopping, individual archival, cooking explanations and measurement/view controls; release packaging, CI, backup/recovery and deployment tooling are implemented; target-specific operational acceptance remains pending. Use JDK 25 (local/CI/Pi must share release 25), Node 24.22.0 (`nvm use`), and the committed backend Gradle 9.8.1 wrapper. No system Gradle or database server is required. SQLite JDBC 3.53.4.0 is the engine in every environment. Use a maintained Java 25 arm64 runtime on the Pi and verify it on the board before cutover.

From the repository root:

```sh
npm ci
npm ci --prefix frontend
cd frontend
npx playwright install chromium
cd ..
./scripts/check.sh
./scripts/build.sh --allow-dirty
./scripts/run-local.sh
```

Checks include lint, TypeScript, component/accessibility, Chromium, and all backend tests. Checks include the credential-free content validator and a browser workflow against an isolated temporary SQLite backend. The check command builds the executable JAR before starting browser servers. Build installs from the npm lockfile and produces `backend/build/libs/mealplan-0.1.0.jar` with React assets. `bootJar` does not run tests. Local startup binds 127.0.0.1:8000 with the explicit local profile and ignored `.local/mealplan/app.sqlite`; it needs no Google credentials. Do not copy production state locally or run alongside another server on port 8000. Local login uses one fixed fixture account; logout ends its session, and the local sign-in link starts a new one. There is no user selector.

Normal commands use strict Gradle locks and SHA-256 verification without changing metadata. Intentional dependency updates require review: edit exact versions, then run `./backend/gradlew -p backend check bootJar --write-locks --write-verification-metadata sha256`, inspect all changed modules/versions and checksums, and rerun ordinary checks/builds. Initial verification metadata trusts HTTPS downloads from Maven Central and the Gradle plugin portal; review the graph rather than claiming independently audited upstream binaries. The wrapper JAR is extracted from the checksum-verified official distribution and matches its published wrapper SHA-256 `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`.

Tests use disposable SQLite and test-only principals, never Google/Telegram or production state. Real Google/Pi acceptance is deferred to the corresponding later tasks. The independent Pi deployment target must be set by its service; do not point the new app at production until the full cutover is approved.

Browser tests launch `BrowserTestApplication` from the test runtime classpath prepared
by `./backend/gradlew -p backend prepareBrowserTests`. Playwright contexts supply
`X-Test-Principal` headers for isolated test accounts; test-only endpoints control the
clock and seed archived entries. Those classes/endpoints are absent from the executable
JAR. The browser launcher uses disposable loopback/local state and the `browser-test`
profile. `scripts/run-local.sh` continues to use the one local fixture account.

Ingredient display equivalences and ordering rules are documented in
[measurements.md](measurements.md). Trusted previews run without credentials or a
database, for example:

```sh
java -jar backend/build/libs/mealplan-0.1.0.jar preview-meals --template-id three-week --anchor-date 2026-10-20 --start-date 2026-10-20 --days 21
java -jar backend/build/libs/mealplan-0.1.0.jar preview-shopping --template-id three-week --anchor-date 2026-10-20 --start-date 2026-10-20 --days 21
```

## Formatting

Run these commands from the repository root using the pinned Node version (`nvm use`):

```sh
npm ci
npm run format       # Rewrite supported source, configuration and documentation files.
npm run format:check # Read-only check; exits nonzero when formatting differs.
```

Prettier and its Java and shell plugins are exact-version development dependencies
in the root `package.json`, with transitive versions and integrity hashes committed
in the root `package-lock.json`. Frontend dependencies remain in `frontend/`.
The same `.prettierrc.json` applies to Java, TypeScript/TSX, JavaScript, CSS, HTML,
JSON, YAML, Markdown and shell scripts. Java uses four spaces; other formatted
files use two. All use LF line endings and a 100-column target. `.editorconfig`
provides matching indentation and line endings for editors.

For automatic formatting on save in VS Code, open the repository root and install
the recommended Prettier extension (`esbenp.prettier-vscode`). The committed
`.vscode/settings.json` enables format on save for the supported languages,
requires a shared configuration, and registers Java and shell file selectors.
EditorConfig is also recommended. Personal VS Code files remain ignored.

Use the repository-local Prettier installation so editors and command-line checks
agree. Other editors can invoke the local `node_modules/.bin/prettier` with
`--write` or `--stdin-filepath`. `scripts/check.sh` installs the root tooling with
`npm ci` and runs the read-only formatting check before application validation.

`.prettierignore` excludes generated output, dependency metadata, vendored workflow
tooling, historical specifications and the immutable content baseline. SQL migrations
are excluded because `MigrationRunner` verifies their exact bytes against stored
SHA-256 checksums; formatting an applied migration would prevent startup. Gradle
Kotlin scripts and other unsupported file types are outside Prettier's coverage;
their indentation follows `.editorconfig`. Do not format generated Gradle wrappers.

To update formatters intentionally, use `npm install --save-dev --save-exact` with
the desired versions, commit both root manifests, rerun `npm run format`, and review
the resulting diff before running `scripts/check.sh`.

## Release and recovery checks

A clean `scripts/build.sh` packages `build/releases/mealplan-FULL_SHA.zip`. Use
`--allow-dirty` only for local development: its manifest records dirty source and
normal verification/deployment rejects it. The bundle contains no mutable state or
credentials. `bootJar` alone does not run tests; CI runs checks before publishing.

Run `scripts/test-release-integrity.sh --clean-build` for an isolated clean-source
build, unchanged lock/checksum metadata, repeatable bundles and tamper rejection.
Run `scripts/test-release-recovery.sh` for real SQLite failed migrations and matched
rollback using the production promotion transaction with controlled service/probe
adapters. `scripts/test-packaged-boundaries.sh` starts a disposable Pi-profile JAR
on loopback port 8000 and needs that port free. CI runs all three after checks/build.

Java operator commands also include `package-release`, `verify-release`,
`backup-database`, `restore-database` and `migrate-database`; scripts supply their
required path/identity options. Backups bind to an exact release manifest and validate
all applied migration checksums. Never use production state for local checks.
See [deployment](deployment.md) for provisioning, artifact credentials, service
hardening, retention, restoration and acceptance still requiring external access.
