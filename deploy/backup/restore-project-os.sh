#!/usr/bin/env sh
set -eu

[ "${PROJECT_OS_RESTORE_CONFIRM:-}" = "RESTORE_PROJECT_OS" ] || {
  echo "Set PROJECT_OS_RESTORE_CONFIRM=RESTORE_PROJECT_OS before restoring." >&2
  exit 2
}
[ $# -ge 1 ] && [ $# -le 2 ] || { echo "Usage: $0 /path/to/postgres.dump [/path/to/minio-data.tar.gz]" >&2; exit 2; }
[ -f "$1" ] || { echo "Backup file not found: $1" >&2; exit 2; }
[ $# -eq 1 ] || [ -f "$2" ] || { echo "MinIO archive not found: $2" >&2; exit 2; }

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
COMPOSE_FILE=${PROJECT_OS_COMPOSE_FILE:-"$ROOT/compose.yaml"}
MINIO_VOLUME=${MINIO_VOLUME:-project-os-platform_minio_data}
case "$MINIO_VOLUME" in
  project-os-platform_minio_data) ;;
  *) echo "Refusing to restore an unexpected MinIO volume: $MINIO_VOLUME" >&2; exit 2 ;;
esac
docker compose -f "$COMPOSE_FILE" stop identity-service project-service work-service operations-service knowledge-service activity-service api-gateway
cat "$1" | docker compose -f "$COMPOSE_FILE" exec -T postgres pg_restore -U project_os_owner -d project_os --clean --if-exists --no-owner
if [ $# -eq 2 ]; then
  ARCHIVE_DIR=$(CDPATH= cd -- "$(dirname "$2")" && pwd)
  ARCHIVE_NAME=$(basename "$2")
  case "$ARCHIVE_NAME" in
    *[!A-Za-z0-9._-]*) echo "MinIO archive name contains unsupported characters" >&2; exit 2 ;;
  esac
  docker run --rm -v "$MINIO_VOLUME:/data" -v "$ARCHIVE_DIR:/backup:ro" alpine:3.20 \
    sh -c "find /data -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -C /data -xzf /backup/$ARCHIVE_NAME"
fi
docker compose -f "$COMPOSE_FILE" start identity-service project-service work-service operations-service knowledge-service activity-service api-gateway
