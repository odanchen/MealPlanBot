#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
npm ci --prefix frontend
npm --prefix frontend run build
./backend/gradlew -p backend bootJar
"$ROOT/scripts/package-release.sh" "$@"
