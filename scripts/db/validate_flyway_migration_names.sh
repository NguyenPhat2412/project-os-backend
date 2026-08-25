#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
MIGRATION_DIR="$ROOT/application/monolith-app/src/main/resources/db/migration/monolith"

[ -d "$MIGRATION_DIR" ] || {
  echo "Migration directory not found: $MIGRATION_DIR" >&2
  exit 2
}

invalid=0
for file in "$MIGRATION_DIR"/*.sql; do
  [ -f "$file" ] || continue
  name=$(basename "$file")
  case "$name" in
    V[0-9]*__*.sql) ;;
    *)
      echo "Invalid Flyway migration filename: $name" >&2
      invalid=1
      ;;
  esac
done

[ "$invalid" -eq 0 ] || exit 1
echo "Flyway migration filenames are valid."
