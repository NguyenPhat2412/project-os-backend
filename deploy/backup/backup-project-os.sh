#!/usr/bin/env sh
set -eu

# Managed/private PostgreSQL backup for the monolith runtime.
# This script never shells into a PostgreSQL container and never backs up a
# legacy service volume. The database URL must point to the canonical public
# schema database and must use TLS in production.
umask 077
BACKUP_DIR=${BACKUP_DIR:-/var/backups/project-os}
RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-30}
ENV_FILE=${PROJECT_OS_ENV_FILE:-}
[ -z "$ENV_FILE" ] || { [ -f "$ENV_FILE" ] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }; set -a; . "$ENV_FILE"; set +a; }
DB_URL=${DB_URL:?DB_URL is required}
DB_USERNAME=${DB_USERNAME:?DB_USERNAME is required}
DB_PASSWORD=${DB_PASSWORD:?DB_PASSWORD is required}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
DEST="$BACKUP_DIR/$STAMP"

case "$BACKUP_DIR" in
  /*) ;;
  *) echo "BACKUP_DIR must be an absolute path" >&2; exit 2 ;;
esac
[ "$BACKUP_DIR" != "/" ] || { echo "BACKUP_DIR must not be /" >&2; exit 2; }
mkdir -p "$DEST"

PG_URL=${DB_URL#jdbc:}
PGPASSWORD="$DB_PASSWORD" pg_dump \
  --dbname="$PG_URL" \
  --username="$DB_USERNAME" \
  --format=custom \
  --no-owner \
  --file="$DEST/postgres-public.dump"

sha256sum "$DEST/postgres-public.dump" > "$DEST/SHA256SUMS"
printf 'created_at=%s\ndatabase_schema=public\nrpo_target=24h\n' \
  "$STAMP" > "$DEST/metadata.txt"

if [ -n "${RCLONE_REMOTE:-}" ]; then
  rclone copy "$DEST" "$RCLONE_REMOTE/$STAMP"
  rclone delete "$RCLONE_REMOTE" --min-age "${RETENTION_DAYS}d"
fi

find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime "+$RETENTION_DAYS" -exec rm -rf -- {} +
echo "Backup created: $DEST"
