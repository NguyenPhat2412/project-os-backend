#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCAL_COMPOSE="$ROOT/compose.monolith.yaml"
PRODUCTION_COMPOSE="$ROOT/compose.monolith.prod.yaml"

fail() {
  echo "Runtime environment contract guard failed: $1" >&2
  exit 1
}

test -f "$LOCAL_COMPOSE" || fail "missing local Compose file"
test -f "$PRODUCTION_COMPOSE" || fail "missing production Compose file"

if rg -n --fixed-strings 'NINEROUTER_CONTAINER_URL' "$ROOT"/compose.monolith*.yaml >/dev/null 2>&1; then
  fail "NINEROUTER_CONTAINER_URL is not canonical"
fi

# Production must receive provider-neutral runtime names. The local Compose
# file keeps MINIO_* aliases for the legacy knowledge module while the
# monolith migration is completed; those aliases must never be copied to the
# production topology.
if rg -n '^\s+(MINIO_ENDPOINT|MINIO_ACCESS_KEY|MINIO_SECRET_KEY|MINIO_BUCKET):' "$PRODUCTION_COMPOSE" >/dev/null 2>&1; then
  fail "legacy MINIO runtime binding is exposed to production"
fi

for key in DB_URL DB_USERNAME DB_PASSWORD OBJECT_STORAGE_ENDPOINT OBJECT_STORAGE_ACCESS_KEY OBJECT_STORAGE_SECRET_KEY OBJECT_STORAGE_BUCKET; do
  rg -n "^\s+${key}:" "$PRODUCTION_COMPOSE" >/dev/null || fail "production Compose does not bind $key"
done

for key in EMAIL_WORKER_ENABLED SMTP_USERNAME SMTP_PASSWORD SMTP_CONNECT_TIMEOUT_MS SMTP_TIMEOUT_MS NINEROUTER_CONNECT_TIMEOUT NINEROUTER_READ_TIMEOUT; do
  rg -n "^\s+${key}:" "$LOCAL_COMPOSE" "$PRODUCTION_COMPOSE" >/dev/null || fail "Compose does not bind $key"
done

echo "Runtime environment contract guard passed."
