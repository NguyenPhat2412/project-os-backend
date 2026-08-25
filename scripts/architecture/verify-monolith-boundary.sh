#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
cd "$ROOT"
failures=''

fail() {
  failures="${failures}${failures:+\n}$1"
}

for legacy_path in api-gateway migration-tool src qa compose.yaml compose.prod.yaml compose.release.yaml; do
  [ ! -e "$legacy_path" ] || fail "legacy path must not exist: $legacy_path"
done

if git ls-files --error-unmatch .env.production >/dev/null 2>&1; then
  fail ".env.production must not be tracked"
fi

if rg -n "<module>api-gateway</module>|api-gateway|identity-service|organization-service|attendance-service|project-service|work-service|knowledge-service|activity-service|operations-service|migration-tool" \
    pom.xml application modules shared compose.monolith.yaml compose.monolith.prod.yaml deploy scripts \
    --glob '!**/target/**' --glob '!**/._*' --glob '!scripts/architecture/verify-monolith-boundary.sh' >/tmp/project-os-monolith-boundary-legacy.txt 2>/dev/null; then
  fail "active source/config references a legacy service or gateway; see /tmp/project-os-monolith-boundary-legacy.txt"
fi

if rg -n "org\.springframework\.web\.client\.(RestClient|WebClient)|RestTemplate|http://[^[:space:]\"']+:(808[1-9]|809[0-9])" \
    application/monolith-app/src/main modules/*/src/main shared/src/main compose.monolith.yaml compose.monolith.prod.yaml deploy scripts \
    --glob '!**/target/**' --glob '!**/._*' --glob '!scripts/architecture/verify-monolith-boundary.sh' >/tmp/project-os-monolith-boundary-http.txt 2>/dev/null; then
  fail "active monolith source/config contains an internal HTTP client or service URL; see /tmp/project-os-monolith-boundary-http.txt"
fi

if rg -n "select[[:space:]]+\*" modules/operations/src/main/java --glob '!**/target/**' --glob '!**/._*' >/tmp/project-os-operations-select-star.txt 2>/dev/null; then
  fail "Operations adapter contains select *; see /tmp/project-os-operations-select-star.txt"
fi

if rg -n "Map<String, Object>|Map<String,Object>" modules/operations/src/main/java/com/projectos/backend/operations/web/OperationsController.java; then
  fail "OperationsController exposes a JDBC Map boundary"
fi

if ! bash scripts/db/validate_flyway_migration_names.sh >/dev/null; then
  fail "Flyway migration filename validation failed"
fi

if [ -n "$failures" ]; then
  printf '%b\n' "$failures" >&2
  exit 1
fi

echo "Monolith boundary verification passed"
