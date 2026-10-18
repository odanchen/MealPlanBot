#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
npm ci --prefix frontend
npm --prefix frontend run build
./backend/gradlew -p backend bootJar
printf '%s\n' "$ROOT/backend/build/libs/mealplan-0.1.0.jar"
