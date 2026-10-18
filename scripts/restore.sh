#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $# == 3 ]] || {
  echo 'Usage: restore.sh BACKUP MATCHING_RELEASE NEW_DISPOSABLE_DATABASE' >&2
  exit 1
}
# Output must not exist. Promotion is a separate stopped-service operation.
exec java -jar "$2/app.jar" restore-database --backup "$1" --release "$2" --output "$3"
