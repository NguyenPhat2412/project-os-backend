#!/usr/bin/env bash
set -Eeuo pipefail

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
ENV_FILE=${PROJECT_OS_ENV_FILE:-"$ROOT/.env.production"}
DEPLOY_REF=${DEPLOY_REF:?DEPLOY_REF is required}
IMAGE_TAG=${IMAGE_TAG:?IMAGE_TAG is required}
IMAGE_NAMESPACE=${IMAGE_NAMESPACE:?IMAGE_NAMESPACE is required}
PREVIOUS_REF=$(cat "$ROOT/.previous-deploy-ref" 2>/dev/null || true)
PREVIOUS_IMAGE_TAG=$(cat "$ROOT/.current-deploy-image-tag" 2>/dev/null || true)

compose() {
  IMAGE_TAG="$1" IMAGE_NAMESPACE="$IMAGE_NAMESPACE" docker compose --env-file "$ENV_FILE" -f "$ROOT/compose.yaml" -f "$ROOT/compose.prod.yaml" -f "$ROOT/compose.release.yaml" "${@:2}"
}

rollback() {
  status=$?
  if [ "$status" -eq 0 ] || [ -z "$PREVIOUS_REF" ] || [ -z "$PREVIOUS_IMAGE_TAG" ]; then
    exit "$status"
  fi

  echo "Release failed; rolling back to $PREVIOUS_REF ($PREVIOUS_IMAGE_TAG)" >&2
  git -C "$ROOT" checkout --detach "$PREVIOUS_REF" || true
  compose "$PREVIOUS_IMAGE_TAG" up -d --wait --wait-timeout 300 || true
  exit "$status"
}
trap rollback EXIT

[ -f "$ENV_FILE" ] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }
PROJECT_OS_ENV_FILE="$ENV_FILE" "$ROOT/deploy/backup/backup-project-os.sh"
compose "$IMAGE_TAG" pull
compose "$IMAGE_TAG" up -d --wait --wait-timeout 300
curl -fsS http://127.0.0.1:18080/actuator/health | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'

printf '%s\n' "$DEPLOY_REF" > "$ROOT/.current-deploy-ref"
printf '%s\n' "$IMAGE_TAG" > "$ROOT/.current-deploy-image-tag"
trap - EXIT
echo "ProjectOS release $DEPLOY_REF ($IMAGE_TAG) is healthy"
