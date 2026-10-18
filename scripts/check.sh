#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
npm ci
npm run format:check
npm ci --prefix frontend
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend test
./backend/gradlew -p backend check bootJar
npm --prefix frontend run test:browser
