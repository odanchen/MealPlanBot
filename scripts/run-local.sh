#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
JAR="$ROOT/backend/build/libs/mealplan-0.1.0.jar"
if [[ ! -f "$JAR" ]]; then "$ROOT/scripts/build.sh" --allow-dirty; fi
mkdir -p "$ROOT/.local/mealplan"
exec java --enable-native-access=ALL-UNNAMED -jar "$JAR" --spring.profiles.active=local --server.address=127.0.0.1 --server.port=8000 --mealplan.database-path="$ROOT/.local/mealplan/app.sqlite"
