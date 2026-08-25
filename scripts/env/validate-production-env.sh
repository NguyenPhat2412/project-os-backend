#!/usr/bin/env sh
set -eu

ENV_FILE=${1:-"$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)/.env.production"}
[ -f "$ENV_FILE" ] || { echo "Production env file not found: $ENV_FILE" >&2; exit 2; }

value() {
  key=$1
  awk -F= -v wanted="$key" '$1 == wanted { sub(/^[^=]*=/, ""); print; exit }' "$ENV_FILE"
}

required='PROJECT_OS_MONOLITH_IMAGE DB_URL DB_USERNAME DB_PASSWORD DB_MIGRATOR_USERNAME DB_MIGRATOR_PASSWORD REDIS_PASSWORD JWT_SECRET INTERNAL_SERVICE_TOKEN CORS_ALLOWED_ORIGINS OBJECT_STORAGE_ENDPOINT OBJECT_STORAGE_ACCESS_KEY OBJECT_STORAGE_SECRET_KEY OBJECT_STORAGE_BUCKET RCLONE_REMOTE BACKUP_DIR BACKUP_RETENTION_DAYS REDIS_SSL SPRING_FLYWAY_ENABLED BOOTSTRAP_ADMIN_ENABLED'
for key in $required; do
  current=$(value "$key")
  [ -n "$current" ] || { echo "Missing production variable: $key" >&2; exit 1; }
  case "$current" in
    *REPLACE_WITH*|*CHANGE_ME*|*example.com*|*example.internal*|*private-db.example*|*s3.example*)
      echo "Placeholder production variable: $key" >&2
      exit 1
      ;;
  esac
done

db_url=$(value DB_URL)
case "$db_url" in
  jdbc:postgresql://*\?*sslmode=verify-full*) ;;
  *) echo "DB_URL must use PostgreSQL TLS sslmode=verify-full" >&2; exit 1 ;;
esac
case "$db_url" in
  *localhost*|*127.0.0.1*) echo "DB_URL must not target localhost in production" >&2; exit 1 ;;
esac

for key in JWT_SECRET INTERNAL_SERVICE_TOKEN DB_PASSWORD DB_MIGRATOR_PASSWORD REDIS_PASSWORD; do
  current=$(value "$key")
  [ "$(printf '%s' "$current" | wc -c | tr -d ' ')" -ge 32 ] || {
    echo "$key must contain at least 32 bytes" >&2
    exit 1
  }
done

[ "$(value COOKIE_SECURE)" = true ] || { echo "COOKIE_SECURE must be true" >&2; exit 1; }
[ "$(value OPENAPI_ENABLED)" = false ] || { echo "OPENAPI_ENABLED must be false" >&2; exit 1; }
[ "$(value OPENAPI_PUBLIC)" = false ] || { echo "OPENAPI_PUBLIC must be false" >&2; exit 1; }
[ "$(value RATE_LIMIT_ENABLED)" = true ] || { echo "RATE_LIMIT_ENABLED must be true" >&2; exit 1; }
[ "$(value REDIS_SSL)" = true ] || { echo "REDIS_SSL must be true" >&2; exit 1; }
[ "$(value SPRING_FLYWAY_ENABLED)" = false ] || { echo "SPRING_FLYWAY_ENABLED must be false for the runtime container" >&2; exit 1; }
[ "$(value CORS_ALLOWED_ORIGINS)" != "*" ] || { echo "Wildcard CORS is forbidden" >&2; exit 1; }

object_storage_endpoint=$(value OBJECT_STORAGE_ENDPOINT)
case "$object_storage_endpoint" in
  https://*) ;;
  *) echo "OBJECT_STORAGE_ENDPOINT must use HTTPS in production" >&2; exit 1 ;;
esac

if [ "$(value BOOTSTRAP_ADMIN_ENABLED)" = true ]; then
  [ -n "$(value BOOTSTRAP_ADMIN_EMAIL)" ] || { echo "BOOTSTRAP_ADMIN_EMAIL is required when bootstrap is enabled" >&2; exit 1; }
  bootstrap_password=$(value BOOTSTRAP_ADMIN_PASSWORD)
  [ "$(printf '%s' "$bootstrap_password" | wc -c | tr -d ' ')" -ge 16 ] || {
    echo "BOOTSTRAP_ADMIN_PASSWORD must contain at least 16 bytes when bootstrap is enabled" >&2
    exit 1
  }
fi

mode=$(stat -f '%Lp' "$ENV_FILE" 2>/dev/null || stat -c '%a' "$ENV_FILE")
case "$mode" in
  600|700) ;;
  *) echo "Set production env permissions to 600; current mode: $mode" >&2; exit 1 ;;
esac
if [ "$mode" = 700 ]; then
  echo "Warning: owner-only mode is 700; use chmod 600 on the VPS" >&2
fi
echo "Production environment policy passed: $ENV_FILE"
