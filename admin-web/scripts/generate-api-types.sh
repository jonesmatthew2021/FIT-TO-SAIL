#!/usr/bin/env bash
#
# DEV-2: the SPA's request/response types are generated from the backend's OpenAPI schema, never
# hand-written. The generated file is committed so this app builds without a JDK, and CI runs
# `--check` to fail the build when the backend's contract has moved and the types have not.
#
#   ./scripts/generate-api-types.sh           regenerate src/api/schema.d.ts
#   ./scripts/generate-api-types.sh --check   fail if the committed file is out of date
#
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
schema="${CREWCOMP_OPENAPI:-$root/../backend/target/openapi/openapi.json}"
target="$root/src/api/schema.d.ts"

if [[ ! -f "$schema" ]]; then
  echo "No OpenAPI schema at $schema" >&2
  echo "Build the backend first:  (cd ../backend && ./mvnw package -DskipTests)" >&2
  exit 1
fi

banner='/**
 * GENERATED FILE — do not edit.
 *
 * Produced from the backend'"'"'s OpenAPI schema by scripts/generate-api-types.sh (DEV-2).
 * Regenerate with `npm run generate:api` after changing a DTO in the backend.
 */
'

generated="$(mktemp)"
trap 'rm -f "$generated"' EXIT

printf '%s' "$banner" > "$generated"
npx --no-install openapi-typescript "$schema" >> "$generated"

if [[ "${1:-}" == "--check" ]]; then
  if ! diff -u "$target" "$generated"; then
    echo >&2
    echo "src/api/schema.d.ts is out of date with the backend OpenAPI schema." >&2
    echo "Run 'npm run generate:api' and commit the result." >&2
    exit 1
  fi
  echo "API types are up to date with $schema"
else
  mv "$generated" "$target"
  trap - EXIT
  echo "Wrote $target"
fi
