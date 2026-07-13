#!/usr/bin/env sh
set -eu

umask 077
ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
COMPOSE_FILE=${PROJECT_OS_COMPOSE_FILE:-"$ROOT/compose.yaml"}
ENV_FILE=${PROJECT_OS_ENV_FILE:-"$ROOT/.env.production"}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/project-os}
RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-30}
MINIO_VOLUME=${MINIO_VOLUME:-project-os-platform_minio_data}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
DEST="$BACKUP_DIR/$STAMP"

case "$BACKUP_DIR" in
  /*) ;;
  *) echo "BACKUP_DIR must be an absolute path" >&2; exit 2 ;;
esac
[ "$BACKUP_DIR" != "/" ] || { echo "BACKUP_DIR must not be /" >&2; exit 2; }

mkdir -p "$DEST"
[ -f "$ENV_FILE" ] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres pg_dump -U project_os_owner -Fc project_os > "$DEST/postgres.dump"
docker run --rm -v "$MINIO_VOLUME:/data:ro" -v "$DEST:/backup" alpine:3.20 \
  tar -C /data -czf /backup/minio-data.tar.gz .
sha256sum "$DEST/postgres.dump" "$DEST/minio-data.tar.gz" > "$DEST/SHA256SUMS"

if [ -n "${RCLONE_REMOTE:-}" ]; then
  rclone copy "$DEST" "$RCLONE_REMOTE/$STAMP"
  rclone delete "$RCLONE_REMOTE" --min-age "${RETENTION_DAYS}d"
fi

find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime "+$RETENTION_DAYS" -exec rm -rf -- {} +
