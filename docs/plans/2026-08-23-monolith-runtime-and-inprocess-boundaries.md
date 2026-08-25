# Modular Monolith Runtime and In-Process Boundaries Plan

**Status:** in-progress

**Goal:** Make `application/monolith-app` the only executable runtime and replace remaining internal HTTP calls with in-process application ports.

## Scope

- Domain modules under `modules/` remain library modules.
- `api-gateway` and root `src/` remain outside the monolith runtime until acceptance tests pass.
- Public `/api/v1/**` contracts remain unchanged.
- No database data is changed in this phase.

## Work sequence

1. [completed] Inventory every `@SpringBootApplication`, Spring Boot Maven plugin, `RestClient`, service URL, and internal controller.
2. [completed] Remove standalone execution configuration from domain module POMs and keep the executable plugin only in `application/monolith-app`.
3. Define application ports: `UserDirectory`, `OrganizationDirectory`, `ProjectAccessPort`, `PermissionChecker`, and `ActivityPublisher`.
4. Implement adapters that call owning-module application services in-process. `OrganizationDirectory` and `ProjectAccessPort` plus their monolith adapters are implemented; remaining ports are pending.
5. Update consumers and remove internal service URL configuration from monolith mode.
6. Verify one security filter chain, application startup, public routes, and no internal HTTP calls.

## Verification

```bash
./mvnw -pl application/monolith-app -am verify
rg -n 'RestClient|WebClient|.*-service-url|@SpringBootApplication' application modules shared
docker compose -f compose.monolith.yaml config --quiet
```

Acceptance requires one executable monolith process, unchanged public API routes,
no gateway dependency, and no HTTP call between domain modules in monolith mode.

## Checkpoint 2026-08-23

- Domain Spring Boot Maven plugins were removed.
- Domain `*ServiceApplication` entrypoint classes were removed.
- `ProjectOsMonolithApplication` now scans the domain modules directly.
- Attendance organization access now has a shared `OrganizationDirectory` port and monolith adapter.
- Activity project-scope lookup now has a `ProjectAccessPort`; the monolith adapter reads `ProjectRepository` in-process and suppresses the legacy HTTP resolver when the port is present.
- Project test wiring now scans shared JPA entities/repositories and runs its Flyway migrations in `public`.
- Build verification passed with Java 21:
  `./mvnw -pl application/monolith-app -am package -DskipTests`.
- Focused project contract verification passed: 3 tests, 0 failures, 0 errors.
- Focused activity contract verification passed: 3 tests, 0 failures, 0 errors.
- Project organization access no longer constructs a `RestClient`; it now consumes `OrganizationDirectory` and the obsolete project/organization service URL settings were removed from project configuration.
- Work authorization and reporting lookups now consume `ProjectPermissionChecker` and `OrganizationDirectory`; work no longer constructs project/organization `RestClient` instances and its internal service URL settings were removed.
- Identity project directory lookup now consumes `ProjectAccessPort`, including actor/root authorization; identity no longer constructs a project `RestClient` and its project service URL setting was removed.
- Identity contract tests passed: 10 tests, 0 failures, 0 errors. Monolith package verification passed again after the new ports.
- Shared resource authorization now consumes `ProjectPermissionChecker`, and outbox dispatch now consumes `ActivityPublisher`; both no longer use internal HTTP or service URL configuration.
- Activity's compatibility scope resolver now uses `ProjectAccessPort`; the active backend scan reports no `RestClient`, `WebClient`, or internal `*-service-url` references under `application`, `modules`, and `shared`.
- Full reactor verification passed after shared-resource conversion: `./mvnw -pl application/monolith-app -am verify` — all reactor modules succeeded.
- Identity, organization, and attendance contract verification also passed earlier in the full run: 10, 7, and 3 tests respectively.
- Monolith security now keeps `/api/v1/internal/**` behind authenticated catch-all authorization, and bootstrap admin creation requires explicit `BOOTSTRAP_ADMIN_ENABLED=true` plus credentials.
- Database documentation now defines the backup, staging-restore, read-only preflight, migration and restore-based rollback procedure in `docs/database/README.md`.
- Verification after the bootstrap hardening passed with Java 21 and Docker Desktop: `./mvnw -pl application/monolith-app -am test` — all reactor modules succeeded; identity 10, organization 7, attendance 3, project 3, work 4, operations 1, knowledge 2, activity 3 tests passed.
- The remaining attendance `OrganizationClient` type is a deprecated source-compatibility alias for the shared `OrganizationDirectory`; it performs no HTTP call and is retained only to avoid breaking standalone module tests during the transition.
- Fresh PostgreSQL 17 startup exposed a missing `organization_permissions` table; this was fixed with the table in `V1__baseline_public_schema.sql` and forward-compatible `V7__add_organization_permissions.sql` for existing schemas baselined at version 1.
- Executable JAR smoke startup passed after the fix: Flyway applied V1-V7, Hibernate `ddl-auto=validate` passed, and `ProjectOsMonolithApplication` started with temporary PostgreSQL and MinIO credentials.
- Final reactor verification passed after the migration fix: `./mvnw -pl application/monolith-app -am verify` — BUILD SUCCESS.
- Added `MonolithStartupTest`, which starts the full monolith context against PostgreSQL Testcontainers and asserts Flyway history V1-V7, canonical public tables, exactly one PK per public table, validated required FKs and `organization_permissions`; the focused test passed with 3 tests, 0 failures, 0 errors.
