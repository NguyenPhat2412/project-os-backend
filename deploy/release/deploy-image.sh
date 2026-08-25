#!/usr/bin/env bash
set -Eeuo pipefail

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
ENV_FILE=${PROJECT_OS_ENV_FILE:-"$ROOT/.env.production"}
DEPLOY_REF=${DEPLOY_REF:?DEPLOY_REF is required}
IMAGE_TAG=${IMAGE_TAG:?IMAGE_TAG is required}
IMAGE_NAMESPACE=${IMAGE_NAMESPACE:?IMAGE_NAMESPACE is required}
PREVIOUS_REF=$(cat "$ROOT/.previous-deploy-ref" 2>/dev/null || true)
PREVIOUS_IMAGE_TAG=$(cat "$ROOT/.current-deploy-image-tag" 2>/dev/null || true)
COMPOSE_FILE="$ROOT/compose.monolith.prod.yaml"

[ -f "$ENV_FILE" ] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }

compose() {
  PROJECT_OS_MONOLITH_IMAGE="ghcr.io/$IMAGE_NAMESPACE/project-os-monolith:$1" \
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "${@:2}"
}

rollback() {
  rc=$?
  if [ "$rc" -eq 0 ] || [ -z "$PREVIOUS_REF" ] || [ -z "$PREVIOUS_IMAGE_TAG" ]; then exit "$rc"; fi
  echo "Release failed; rolling back application image $PREVIOUS_IMAGE_TAG" >&2
  compose "$PREVIOUS_IMAGE_TAG" up -d --wait --wait-timeout 300 || true
  exit "$rc"
}
trap rollback EXIT

PROJECT_OS_ENV_FILE="$ENV_FILE" "$ROOT/deploy/backup/backup-project-os.sh"
DB_URL=$(grep '^DB_URL=' "$ENV_FILE" | cut -d= -f2-)
DB_MIGRATOR_USERNAME=$(grep '^DB_MIGRATOR_USERNAME=' "$ENV_FILE" | cut -d= -f2-)
DB_MIGRATOR_PASSWORD=$(grep '^DB_MIGRATOR_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
DB_URL="$DB_URL" DB_MIGRATOR_USERNAME="$DB_MIGRATOR_USERNAME" DB_MIGRATOR_PASSWORD="$DB_MIGRATOR_PASSWORD" \
  "$ROOT/deploy/release/run-monolith-migrations.sh"
compose "$IMAGE_TAG" pull
compose "$IMAGE_TAG" up -d --wait --wait-timeout 300
curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'

printf '%s\n' "$DEPLOY_REF" > "$ROOT/.current-deploy-ref"
printf '%s\n' "$IMAGE_TAG" > "$ROOT/.current-deploy-image-tag"
trap - EXIT
echo "ProjectOS monolith release $DEPLOY_REF ($IMAGE_TAG) is healthy"
