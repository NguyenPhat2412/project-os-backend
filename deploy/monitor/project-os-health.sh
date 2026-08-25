#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
COMPOSE_FILE=${PROJECT_OS_COMPOSE_FILE:-"$ROOT/compose.monolith.prod.yaml"}
ENV_FILE=${PROJECT_OS_ENV_FILE:-"$ROOT/.env.production"}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/project-os}
MAX_DISK_PERCENT=${PROJECT_OS_MAX_DISK_PERCENT:-85}
MAX_MEMORY_PERCENT=${PROJECT_OS_MAX_MEMORY_PERCENT:-90}
MAX_BACKUP_AGE_SECONDS=${PROJECT_OS_MAX_BACKUP_AGE_SECONDS:-93600}
LOGIN_FAILURE_LIMIT=${PROJECT_OS_LOGIN_FAILURE_LIMIT:-10}
LOGIN_FAILURE_WINDOW=${PROJECT_OS_LOGIN_FAILURE_WINDOW:-5m}
ALERT_URL=${PROJECT_OS_ALERT_WEBHOOK_URL:-}
ALERT_FORMAT=${PROJECT_OS_ALERT_WEBHOOK_FORMAT:-slack}
FORCED_FAILURE=${PROJECT_OS_MONITOR_FORCED_FAILURE:-}

failures=''

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

add_failure() {
  failures="${failures}${failures:+; }$1"
}

send_alert() {
  [ -n "$ALERT_URL" ] || return 0
  message="[ProjectOS] $failures"
  if [ "$ALERT_FORMAT" = discord ]; then
    payload=$(printf '{"content":"%s"}' "$message")
  else
    payload=$(printf '{"text":"%s"}' "$message")
  fi
  curl -fsS --max-time 10 -H 'Content-Type: application/json' -d "$payload" "$ALERT_URL" >/dev/null || true
}

[ -f "$ENV_FILE" ] || add_failure "missing environment file"
[ -z "$FORCED_FAILURE" ] || add_failure "$FORCED_FAILURE"

containers=$(compose ps -q 2>/dev/null || true)
[ -n "$containers" ] || add_failure "no ProjectOS containers"
for container in $containers; do
  status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo missing)
  case "$status" in healthy|running) ;; *) add_failure "container $container is $status" ;; esac
done

curl -fsS --max-time 10 http://127.0.0.1:8080/actuator/health | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' || add_failure "monolith health"
compose exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" ping' | grep -q '^PONG$' || add_failure "Redis ping"

disk_percent=$(df -P "$ROOT" | awk 'NR == 2 { gsub(/%/, "", $5); print $5 }')
[ -n "$disk_percent" ] && [ "$disk_percent" -lt "$MAX_DISK_PERCENT" ] || add_failure "disk ${disk_percent:-unknown}%"

if [ -r /proc/meminfo ]; then
  total=$(awk '/MemTotal:/ { print $2 }' /proc/meminfo)
  available=$(awk '/MemAvailable:/ { print $2 }' /proc/meminfo)
  if [ -n "$total" ] && [ -n "$available" ] && [ "$total" -gt 0 ]; then
    memory_percent=$(( (total - available) * 100 / total ))
    [ "$memory_percent" -lt "$MAX_MEMORY_PERCENT" ] || add_failure "RAM ${memory_percent}%"
  fi
fi

latest_backup=$(find "$BACKUP_DIR" -type f -name postgres-public.dump -printf '%T@\n' 2>/dev/null | sort -nr | head -n 1 || true)
if [ -z "$latest_backup" ]; then
  add_failure "backup missing"
else
  backup_age=$(( $(date +%s) - ${latest_backup%.*} ))
  [ "$backup_age" -lt "$MAX_BACKUP_AGE_SECONDS" ] || add_failure "backup stale (${backup_age}s)"
fi

login_failures=$(compose logs --since "$LOGIN_FAILURE_WINDOW" monolith-app 2>/dev/null | grep -c 'auth_login_failed' || true)
[ "$login_failures" -lt "$LOGIN_FAILURE_LIMIT" ] || add_failure "login failures ${login_failures}/${LOGIN_FAILURE_WINDOW}"

if [ -n "$failures" ]; then
  send_alert
  echo "ProjectOS monitor failed: $failures" >&2
  exit 1
fi

echo "ProjectOS monitor healthy"
