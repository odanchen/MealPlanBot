#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ $# == 2 && "$1" == --commit ]] || {
  echo 'Usage: deploy-pi.sh --commit HEAD|FULL_SHA' >&2
  exit 1
}
cd "$ROOT"
[[ -z "$(git status --porcelain)" ]] || {
  echo 'Deployment requires a clean checkout.' >&2
  exit 1
}
SHA="$(git rev-parse "$2^{commit}")"
[[ "$SHA" == "$(git rev-parse HEAD)" ]] || {
  echo 'Requested commit must match the checkout.' >&2
  exit 1
}
git fetch origin main
git merge-base --is-ancestor "$SHA" origin/main || {
  echo 'Commit must be on trusted origin/main.' >&2
  exit 1
}
[[ "$(uname -m)" == aarch64 ]] || {
  echo 'Pi deployment requires arm64.' >&2
  exit 1
}
java -version 2>&1 | head -1 | grep -Eq 'version "25[.\"]' || {
  echo 'Java 25 is required.' >&2
  exit 1
}
for COMMAND in gh jq unzip curl flock sha256sum; do command -v "$COMMAND" > /dev/null; done
STATE=/var/lib/mealplan
[[ -d "$STATE" && -w "$STATE" ]] || {
  echo 'Provision writable /var/lib/mealplan first.' >&2
  exit 1
}
exec 9> "$STATE/deploy.lock"
flock -n 9 || {
  echo 'Another deployment is running.' >&2
  exit 1
}
[[ "$(df -Pk "$STATE" | awk 'NR==2 {print $4}')" -ge 524288 ]] || {
  echo 'At least 512 MiB free disk is required.' >&2
  exit 1
}
REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
RUN="$(gh api "repos/$REPO/actions/workflows/ci.yml/runs?event=push&status=success&head_sha=$SHA" --jq '.workflow_runs[] | select(.head_branch == "main") | .id' | head -1)"
[[ -n "$RUN" ]] || {
  echo 'No successful trusted-main build; rebuild this commit.' >&2
  exit 1
}
STAGE="$(mktemp -d "$STATE/.deploy-XXXXXXXX")"
trap 'rm -rf -- "$STAGE"' EXIT
gh run download "$RUN" --repo "$REPO" --name "mealplan-$SHA" --dir "$STAGE/download" || {
  echo 'Artifact missing or expired; rebuild this exact commit.' >&2
  exit 1
}
BUNDLE="$STAGE/download/mealplan-$SHA.zip"
mkdir "$STAGE/release"
# Reject zip paths before extraction; never allow absolute/traversal names or duplicate entries.
unzip -Z1 "$BUNDLE" > "$STAGE/paths"
if grep -Eq '(^/|(^|/)\.\.(/|$)|\\)' "$STAGE/paths" || [[ -n "$(sort "$STAGE/paths" | uniq -d)" ]]; then
  echo 'Unsafe release archive.' >&2
  exit 1
fi
unzip -q "$BUNDLE" -d "$STAGE/release"
# Verify using the current trusted checkout build's inventory contract and the artifact's Java command.
(cd "$STAGE/release" && sha256sum --check --strict checksums.sha256)
java -jar "$STAGE/release/app.jar" verify-release --release "$STAGE/release" --commit "$SHA"
mkdir -p "$STATE/releases"
RELEASE="$STATE/releases/$SHA"
[[ ! -e "$RELEASE" ]] || {
  echo 'Release already staged; inspect it before retrying.' >&2
  exit 1
}
mv "$STAGE/release" "$RELEASE"
source "$ROOT/scripts/promote-release.sh"
