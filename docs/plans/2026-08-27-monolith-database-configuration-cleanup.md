# Monolith Database Configuration Cleanup

## Goal

Remove legacy multi-database password, role and Compose configuration so the
modular monolith uses one PostgreSQL database and one runtime password.

## Implemented changes

- Removed the eight service-specific database password keys from `.env` and
  `.env.example` without displaying the local secret values.
- Removed those keys from the monolith PostgreSQL Compose environment.
- Simplified `deploy/postgres/init-services.sh` to install only `pg_trgm`,
  `uuid-ossp` and `pgcrypto`; it no longer creates service roles or schemas.
- Updated module fallback datasource credentials to use `DB_USERNAME`/
  `POSTGRES_USER`, `DB_PASSWORD`/`POSTGRES_PASSWORD` and native port 5432.
- Kept module-only schema settings solely for existing standalone module
  contract tests; the active monolith remains configured for `public`.
- Removed the legacy keys from backend and frontend administrator
  environment whitelists.

## Verification

- `./mvnw clean test-compile` was attempted on the active backend. Maven hit a
  macOS external-volume failure deleting generated `target` metadata; the
  subsequent `./mvnw test-compile` completed successfully.
- The isolated monolith worktree passed `./mvnw clean test-compile` and the
  full `./mvnw -pl application/monolith-app -am verify` gate.
- `docker compose -f compose.monolith.yaml config --quiet` passed with only
  the unified database password requirement.
- FE `npm run typecheck` passed.
- Active source/config scans report no service-specific database password keys,
  module password placeholders, or legacy role/schema creation in the init
  script.

## Safety

- No production database was changed.
- Existing database roles and schemas are not deleted by this source cleanup;
  catalog cleanup requires a separate backup/preflight operation.
- Existing dirty worktree changes were preserved.
