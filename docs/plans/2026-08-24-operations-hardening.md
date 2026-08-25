# Operations, outbox and FE facade hardening

Status: completed locally on 2026-08-24

## Completed

- Replaced raw Operations mutation maps at the controller boundary with the
  typed `OperationsMutationRequest` DTO.
- Added tenant-safe CRUD for legacy `contracts`, `kpi`, `leave-balances` and
  `teams` tables, plus CRUD for the verified global `master-data` catalog.
- Added V12 to normalize leave-balance employee references to UUIDs without
  rewriting the original employee-code column or deleting history.
- Added V13 as an empty, tenant-owned `organization_branches` table and exposed
  its CRUD through the same typed Operations boundary.
- Enabled outbox dispatch by default in the monolith and made the polling
  interval configurable through environment variables.
- Restricted actuator exposure to public health only. Info and OpenAPI/Swagger
  require authentication unless explicitly enabled for a controlled local
  environment.
- Migrated active FE domain callers to `organizations/current` resolution and
  removed the flat legacy route handlers for the migrated domains.
- Changed master-data updates to `PATCH`, matching the backend contract.

## Evidence

- Local preflight: 44 public tables, 55 validated foreign keys, zero duplicate
  FKs, zero missing PKs, zero orphan rows, zero tenant mismatches, zero
  unresolved legacy references and zero duplicate business identifiers.
- `./mvnw -pl application/monolith-app -am verify`: passed with Java 21 and
  PostgreSQL Testcontainers; monolith startup test ran 7 tests successfully.
- FE `typecheck`, `architecture:check`, `lint` and `build`: passed.
- Local runtime: `/actuator/health` returns `200`/`UP`, while `/actuator/info`
  returns `401` without authentication.

## Remaining boundary

The global legacy company-profile table is intentionally not exposed as a
tenant CRUD owner. Bank options are read from the verified `master-data` owner
with category `BANK`; an empty catalog remains empty and never falls back to
demo values.
