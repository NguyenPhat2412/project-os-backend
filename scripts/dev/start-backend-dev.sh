#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${BACKEND_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Không tìm thấy file môi trường local: ${ENV_FILE}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

: "${POSTGRES_HOST_PORT:=15433}"
: "${REDIS_HOST_PORT:=16379}"
: "${MINIO_API_PORT:=19000}"
: "${ZIPKIN_PORT:=19411}"

echo "Dừng container backend để tránh chiếm cổng 8080..."
docker compose --env-file "${ENV_FILE}" -f "${BACKEND_DIR}/compose.monolith.yaml" stop monolith-app >/dev/null 2>&1 || true

echo "Khởi động các service hạ tầng cần thiết..."
docker compose --env-file "${ENV_FILE}" -f "${BACKEND_DIR}/compose.monolith.yaml" up -d postgres redis minio zipkin

export SPRING_PROFILES_ACTIVE="dev"
export SERVER_PORT="8080"
export DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_HOST_PORT}/project_os"
export DB_USERNAME="project_os_owner"
export DB_PASSWORD="${POSTGRES_PASSWORD}"
export REDIS_HOST="127.0.0.1"
export REDIS_PORT="${REDIS_HOST_PORT}"
export MINIO_ENDPOINT="http://127.0.0.1:${MINIO_API_PORT}"
export MINIO_ACCESS_KEY="${MINIO_ROOT_USER}"
export MINIO_SECRET_KEY="${MINIO_ROOT_PASSWORD}"
export MINIO_BUCKET="${MINIO_BUCKET:-project-os}"
export ZIPKIN_ENDPOINT="http://127.0.0.1:${ZIPKIN_PORT}/api/v2/spans"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:3000,http://127.0.0.1:3000}"

cd "${BACKEND_DIR}"

current_source_stamp() {
  find application modules shared -path '*/src/*' -type f \( -name '*.java' -o -name '*.yml' -o -name '*.yaml' -o -name '*.sql' \) \
    ! -name '._*' -exec stat -f '%m:%z:%N' {} + 2>/dev/null | LC_ALL=C sort | shasum -a 256 | awk '{print $1}'
}

backend_pid=''
stop_backend() {
  if [[ -n "${backend_pid}" ]] && kill -0 "${backend_pid}" 2>/dev/null; then
    kill "${backend_pid}" 2>/dev/null || true
    wait "${backend_pid}" 2>/dev/null || true
  fi
}
trap stop_backend EXIT INT TERM

start_backend() {
  echo "Backend development đang chạy ngoài Docker tại http://127.0.0.1:8080"
  ./mvnw -q -f pom.xml install -DskipTests
  ./mvnw -f application/monolith-app/pom.xml spring-boot:run \
    -Dspring-boot.run.fork=false \
    -Dspring-boot.run.addResources=true \
    -Dspring-boot.run.jvmArguments=-Dspring.classformat.ignore=true &
  backend_pid=$!
}

last_stamp="$(current_source_stamp)"
start_backend

while true; do
  sleep 1
  next_stamp="$(current_source_stamp)"
  if [[ "${next_stamp}" != "${last_stamp}" ]]; then
    echo "Phát hiện thay đổi backend, đang compile và reload..."
    stop_backend
    last_stamp="${next_stamp}"
    start_backend
  fi
done
