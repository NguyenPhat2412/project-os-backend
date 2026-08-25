#!/usr/bin/env sh
set -eu

[ "${PROJECT_OS_RESTORE_CONFIRM:-}" = "RESTORE_PROJECT_OS" ] || {
  echo "Set PROJECT_OS_RESTORE_CONFIRM=RESTORE_PROJECT_OS before restoring." >&2
  exit 2
}
[ "${PROJECT_OS_RESTORE_TARGET:-staging}" = "staging" ] || {
  echo "Refusing restore unless PROJECT_OS_RESTORE_TARGET=staging." >&2
  exit 2
}
[ $# -eq 1 ] || { echo "Usage: $0 /path/to/postgres-public.dump" >&2; exit 2; }
BACKUP_FILE=$1
[ -f "$BACKUP_FILE" ] || { echo "Backup file not found: $BACKUP_FILE" >&2; exit 2; }

BACKUP_PATH=$(CDPATH= cd -- "$(dirname "$BACKUP_FILE")" && pwd)
CHECKSUM_FILE="$BACKUP_PATH/SHA256SUMS"
[ -f "$CHECKSUM_FILE" ] || {
  echo "Refusing restore: SHA256SUMS is missing beside the backup" >&2
  exit 2
}
expected_checksum=$(awk '$2 == "postgres-public.dump" { print $1; exit }' "$CHECKSUM_FILE")
[ -n "$expected_checksum" ] || {
  echo "Refusing restore: checksum entry for postgres-public.dump is missing" >&2
  exit 2
}
if command -v sha256sum >/dev/null 2>&1; then
  actual_checksum=$(sha256sum "$BACKUP_FILE" | awk '{print $1}')
else
  actual_checksum=$(shasum -a 256 "$BACKUP_FILE" | awk '{print $1}')
fi
[ "$actual_checksum" = "$expected_checksum" ] || {
  echo "Refusing restore: backup checksum mismatch" >&2
  exit 2
}

DB_URL=${DB_URL:?DB_URL is required}
DB_USERNAME=${DB_USERNAME:?DB_USERNAME is required}
DB_PASSWORD=${DB_PASSWORD:?DB_PASSWORD is required}
APP_COMPOSE_FILE=${PROJECT_OS_COMPOSE_FILE:-compose.monolith.prod.yaml}
APP_ENV_FILE=${PROJECT_OS_ENV_FILE:-.env.staging}
[ -f "$APP_ENV_FILE" ] && { set -a; . "$APP_ENV_FILE"; set +a; }

case "$DB_URL" in
  *project_os*) ;;
  *) echo "Refusing restore: DB_URL does not identify project_os" >&2; exit 2 ;;
esac

echo "Restoring into staging target. Stop monolith-app before continuing."
if command -v docker >/dev/null 2>&1 && [ -f "$APP_ENV_FILE" ]; then
  docker compose --env-file "$APP_ENV_FILE" -f "$APP_COMPOSE_FILE" stop monolith-app || true
fi

PG_URL=${DB_URL#jdbc:}
PGPASSWORD="$DB_PASSWORD" pg_restore \
  --dbname="$PG_URL" \
  --username="$DB_USERNAME" \
  --clean --if-exists --no-owner "$BACKUP_FILE"

echo "Restore completed. Start the monolith and run the staging smoke test before using the database."
