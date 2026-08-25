#!/usr/bin/env sh
set -eu

# Run before starting a new application image. Use the least-privilege
# migration role; the runtime container deliberately has Flyway disabled.
ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
DB_URL=${DB_URL:?DB_URL is required}
DB_USERNAME=${DB_MIGRATOR_USERNAME:?DB_MIGRATOR_USERNAME is required}
DB_PASSWORD=${DB_MIGRATOR_PASSWORD:?DB_MIGRATOR_PASSWORD is required}
FLYWAY_IMAGE=${FLYWAY_IMAGE:-flyway/flyway:10-alpine}
MIGRATION_DIR="$ROOT/application/monolith-app/src/main/resources/db/migration/monolith"

[ -d "$MIGRATION_DIR" ] || { echo "Migration directory not found: $MIGRATION_DIR" >&2; exit 2; }
sh "$ROOT/scripts/db/validate_flyway_migration_names.sh"

docker run --rm \
  -v "$MIGRATION_DIR:/flyway/sql:ro" \
  "$FLYWAY_IMAGE" \
  -url="$DB_URL" \
  -user="$DB_USERNAME" \
  -password="$DB_PASSWORD" \
  -schemas=public \
  -defaultSchema=public \
  -locations=filesystem:/flyway/sql \
  validate

docker run --rm \
  -v "$MIGRATION_DIR:/flyway/sql:ro" \
  "$FLYWAY_IMAGE" \
  -url="$DB_URL" \
  -user="$DB_USERNAME" \
  -password="$DB_PASSWORD" \
  -schemas=public \
  -defaultSchema=public \
  -locations=filesystem:/flyway/sql \
  migrate
