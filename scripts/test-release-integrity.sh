#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT
if [[ "${1:-}" == --clean-build ]]; then
  mkdir "$WORK/source"
  (cd "$ROOT" && git ls-files --cached --others --exclude-standard -z | tar --null -T - -cf -) | tar -xf - -C "$WORK/source"
  cd "$WORK/source"
  git init -q
  git add .
  git -c user.name='Release verification' -c user.email='release@example.test' commit -qm 'Isolated implementation snapshot'
  ./scripts/build.sh
  git diff --exit-code -- backend/gradle.lockfile backend/gradle/verification-metadata.xml frontend/package-lock.json package-lock.json
  [[ -z "$(git status --porcelain)" ]]
  ROOT="$WORK/source"
fi
cd "$ROOT"
JAR="$ROOT/backend/build/libs/mealplan-0.1.0.jar"
SHA="$(git rev-parse HEAD)"
package() {
  java -jar "$JAR" package-release --root "$ROOT" --jar "$1" --content "$ROOT/content" --commit "$SHA" --dirty false --output "$2"
}
verify() {
  java -jar "$JAR" verify-release --release "$1" --commit "$SHA"
}
reject() {
  if "$@"; then
    echo 'Expected release rejection did not occur.' >&2
    exit 1
  fi
}
if [[ "${1:-}" == --clean-build ]]; then
  touch "$ROOT/release-dirty-source-test"
  reject "$ROOT/scripts/package-release.sh"
  rm "$ROOT/release-dirty-source-test"
elif [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then
  reject "$ROOT/scripts/package-release.sh"
fi
package "$JAR" "$WORK/first.zip"
package "$JAR" "$WORK/second.zip"
cmp "$WORK/first.zip" "$WORK/second.zip"
mkdir "$WORK/release"
unzip -q "$WORK/first.zip" -d "$WORK/release"
verify "$WORK/release"
for CASE in payload manifest checksums missing extra; do
  cp -R "$WORK/release" "$WORK/$CASE"
  case "$CASE" in
    payload) printf 'tamper' >> "$WORK/$CASE/app.jar" ;;
    manifest) printf ' ' >> "$WORK/$CASE/manifest.json" ;;
    checksums) printf 'invalid\n' >> "$WORK/$CASE/checksums.sha256" ;;
    missing) rm "$WORK/$CASE/content/glossary.yaml" ;;
    extra) touch "$WORK/$CASE/extra" ;;
  esac
  reject verify "$WORK/$CASE"
done
mkdir "$WORK/plain"
printf 'plain\n' > "$WORK/plain/example.txt"
jar --create --file "$WORK/plain.jar" -C "$WORK/plain" .
reject package "$WORK/plain.jar" "$WORK/plain.zip"
# This explicit dirty release cannot be promoted with the normal verification command.
java -jar "$JAR" package-release --root "$ROOT" --jar "$JAR" --content "$ROOT/content" --commit "$SHA" --dirty true --output "$WORK/dirty.zip"
mkdir "$WORK/dirty"
unzip -q "$WORK/dirty.zip" -d "$WORK/dirty"
reject verify "$WORK/dirty"
printf 'Release integrity, repeatability and requested clean-build checks passed.\n'
