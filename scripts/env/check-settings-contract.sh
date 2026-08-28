#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
backend_settings_files=(
  "$ROOT/modules/organization/src/main/java/com/projectos/backend/organization/EnvironmentConfigCatalog.java"
  "$ROOT/modules/organization/src/main/java/com/projectos/backend/organization/EnvironmentConfigService.java"
  "$ROOT/modules/organization/src/main/java/com/projectos/backend/organization/EnvironmentConfigValidation.java"
  "$ROOT/modules/organization/src/main/java/com/projectos/backend/organization/domain/OrganizationApplicationService.java"
)
frontend_settings_files=(
  "$ROOT/../fe/src/features/admin/api/environment-config-api.ts"
  "$ROOT/../fe/src/features/admin/types/admin-settings.types.ts"
  "$ROOT/../fe/src/features/admin/components/AdminSettingsFeature.tsx"
)

legacy_backend_keys=(
  GATEWAY_PORT PROJECT_OS_API_PUBLIC_URL PROJECT_OS_API_INTERNAL_URL
  POSTGRES_HOST POSTGRES_PORT POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD
  NEXT_PUBLIC_WS_URL WS_PORT S3_BUCKET S3_REGION S3_ACCESS_KEY S3_SECRET_KEY S3_ENDPOINT
  MINIO_ROOT_USER MINIO_ROOT_PASSWORD ANTHROPIC_API_KEY GEMINI_API_KEY
  POSTGRES_HOST_PORT NATIVE_DB_PASSWORD PROJECT_OS_ENV_HOST_PATH
)

for key in "${legacy_backend_keys[@]}"; do
  if rg -n --fixed-strings "$key" "${backend_settings_files[@]}" >/dev/null; then
    echo "Legacy key remains in active backend Settings contract: $key" >&2
    exit 1
  fi
done

for key in S3_BUCKET S3_REGION S3_ACCESS_KEY S3_SECRET_KEY S3_ENDPOINT ANTHROPIC_API_KEY GEMINI_API_KEY; do
  if rg -n --fixed-strings "$key" "$ROOT/../fe/.env" "$ROOT/../fe/.env.example" "$ROOT/../fe/src/features/admin" "$ROOT/../fe/src/lib" >/dev/null; then
    echo "Legacy frontend environment key remains active: $key" >&2
    exit 1
  fi
done

for key in GATEWAY_PORT READ_MODEL_CACHE_TTL_SECONDS PROJECT_OS_ENV_HOST_PATH; do
  if rg -n --fixed-strings "$key" "$ROOT/.env" "$ROOT/.env.example" >/dev/null; then
    echo "Obsolete backend environment key remains: $key" >&2
    exit 1
  fi
done

if rg -n --fixed-strings 'NINEROUTER_CONTAINER_URL' "$ROOT/compose.monolith.yaml" "$ROOT/compose.monolith.prod.yaml" >/dev/null 2>&1; then
  echo "Obsolete container-specific 9Router variable remains in Compose." >&2
  exit 1
fi

echo "Environment Settings contract guard passed."
