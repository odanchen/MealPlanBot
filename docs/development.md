# Local development

The Java/React foundation is the only application runtime in this checkout. The legacy Python implementation has been removed; no installed production services have been changed. All six user stories are implemented, including recurring shopping, individual archival, cooking explanations and measurement/view controls; release/deployment tooling and operational acceptance remain in the backlog. Use JDK 25 (local/CI/Pi must share release 25), Node 24.22.0 (`nvm use`), and the committed backend Gradle 9.8.1 wrapper. No system Gradle or database server is required. SQLite JDBC 3.53.4.0 is the engine in every environment. Use a maintained Java 25 arm64 runtime on the Pi when deployment is implemented.

From the repository root:

```sh
npm ci --prefix frontend
cd frontend
npx playwright install chromium
cd ..
./scripts/check.sh
./scripts/build.sh
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
