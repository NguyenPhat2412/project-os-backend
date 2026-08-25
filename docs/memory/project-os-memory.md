# Project OS Backend Memory

This file is the durable summary of decisions and current implementation state.
Before starting a future backend task, read this file and the linked current plan.
Update it when a plan changes status or a cross-cutting decision is accepted.

## Current target architecture

- Runtime: one Spring Boot process, `application/monolith-app`.
- Domain modules: `modules/identity`, `organization`, `attendance`, `project`, `work`, `operations`, `knowledge`, and `activity`.
- Shared libraries: `shared/platform` and `shared/resource`.
- Database: PostgreSQL `public` schema is canonical; Flyway migrations belong to the monolith application.
- API: public routes remain under `/api/v1/**`.
- Frontend: receives dynamic data from backend APIs; no mock business data.

## Completed decisions and changes

- Backend directory and Maven naming no longer use `*-service` for active domain modules.
- The executable artifact is `monolith-app`.
- Maven parent paths for moved modules use `../../pom.xml`.
- Domain modules no longer package standalone Spring Boot executables or contain `*ServiceApplication` entrypoints.
- Attendance organization lookups now depend only on the shared `OrganizationDirectory` port; the deprecated `OrganizationClient` compatibility alias and monolith import were removed.
- Activity project-scope lookup now has a shared `ProjectAccessPort` and monolith adapter backed by `ProjectRepository`; the legacy HTTP resolver is conditional fallback only.
- `api-gateway`, root legacy `src/`, `migration-tool`, `qa`, and service-based Compose files were removed from the active worktree on 2026-08-25 after the cleanup inventory; they are not supported runtime paths.
- Naming rules are defined in [backend-naming-convention.md](../architecture/backend-naming-convention.md).
- The documentation system was standardized on 2026-08-23.
- The canonical Java namespace and Maven group ID are now `com.projectos.backend`; the former vendor-specific namespace has been removed from backend source, tests and build metadata.
- Java package roots in active backend code match `com/projectos/backend`; public routes and database identifiers were unchanged.
- FE–BE contract normalization checkpoint is recorded in [the current plan](../plans/2026-08-23-fe-be-contract-normalization.md): the shared FE client unwraps mutation envelopes, preserves page metadata, resolves organization-scoped routes, and migrated flows no longer create synthetic business records.
- Permission replacement is namespace-aware (`page:*` and `component:*` are independent). Project read models and schema-backed Operations APIs are available in the monolith; unsupported domains return an explicit missing-schema-owner error.
- Missing public-schema owners now have empty-table CRUD for positions, training courses, regulations, company mailboxes and report definitions via the monolith. Migration V8 is schema-only and inserts no fake/demo business rows.
- Operations writes now use the typed `OperationsMutationRequest` contract. CRUD covers positions, training courses, regulations, company mailboxes, report definitions, contracts, KPI evaluations, leave balances, teams and global master catalogs. Legacy text identifiers remain text business identifiers; UUID compatibility columns are used for tenant-safe employee references.
- Public schema hardening is canonical through V22: V9 removes duplicate FKs and adds ownership constraints, V10–V12 normalize verified legacy UUID references, V13 adds the empty `organization_branches` owner table, and V14–V22 add employee/account, settings, attendance-location, soft-delete, audit/integrity, offboarding, recycle-bin, performance-scoring and position-profile domains. Original varchar business-code columns remain unchanged.
- Monolith outbox dispatch is enabled by default (`OUTBOX_ENABLED=true`, interval configurable through `OUTBOX_INTERVAL_MS`). Actuator exposes health publicly; info and documentation endpoints require authentication unless explicitly enabled with `OPENAPI_PUBLIC=true`.
- Production deployment now has a monolith-only Compose profile (`compose.monolith.prod.yaml`) with no PostgreSQL container, explicit production secrets, Redis authentication, non-root/read-only container hardening, readiness healthcheck and loopback-only application binding. Managed/private PostgreSQL must use TLS and a runtime role without DDL permissions.
- Backup/restore scripts now target the canonical PostgreSQL `public` schema through `pg_dump`/`pg_restore`; restore is staging-only by default and requires explicit confirmation. Backup artifacts include SHA-256 checksums and are suitable for offsite retention of at least 30 days.
- Nginx production guidance terminates HTTPS, redirects HTTP, limits login traffic, exposes only health and proxies `/api/v1/**` to the monolith at `127.0.0.1:8080`. No gateway or service-based deployment path is current.
- Production hardening checkpoint 2026-08-24: `compose.monolith.prod.yaml` is validated with no PostgreSQL service; the runtime uses external `DB_URL` TLS, Redis password/TLS settings, `SPRING_FLYWAY_ENABLED=false`, and the dedicated `deploy/release/run-monolith-migrations.sh` runner. `deploy/backup/backup-project-os.sh` creates custom-format PostgreSQL backups with SHA-256 checksums and offsite retention; restore is confirmation- and staging-target guarded.
- Operations public responses now use the explicit `OperationsResourceDto` through `OperationsResourceMapper`; the JDBC adapter remains an internal compatibility implementation and is still scheduled for per-resource repository extraction.
- Redis authentication rate limiting is wired through `AuthRateLimitService`: five login attempts per email/IP in 15 minutes and ten refresh attempts per refresh-session/IP in five minutes, enabled by `RATE_LIMIT_ENABLED=true` only in production.
- Verification checkpoint 2026-08-25: full monolith reactor verification passed after the Operations typed-boundary and explicit-projection changes. Active Testcontainers suites report zero failures/errors; `MonolithStartupTest` passed 14/14 against PostgreSQL 17 with Flyway V1–V22.
- Backend folder/import cleanup checkpoint 2026-08-25: `OrganizationDirectoryAdapter` now exposes only the shared `OrganizationDirectory` port; attendance production code and contract tests no longer import the deprecated `OrganizationClient` alias. The monolith schema gate now asserts the canonical Flyway V16 baseline, and focused monolith tests pass 9/9. macOS `._*` metadata artifacts were removed from backend source and generated targets.
- On 2026-08-25, the non-example `.env.production` deployment manifest was created but deliberately fail-closed with `REPLACE_WITH_*` markers because the repository does not contain the real managed PostgreSQL, frontend domain or private object-storage credentials. `scripts/env/validate-production-env.sh` must pass after those external values are supplied; the file is Git-ignored and must be mode 600 on the VPS.
- FE organization domain callers now resolve through `organizations/current`; the removed flat Next API handlers are no longer part of the active FE compatibility surface. Unsupported/unowned legacy domains must remain explicit rather than receiving fallback data.
- Frontend department selectors use live organization departments; tenant/domain/policy fallback business values and local position-role seed profiles are not created in the browser.
- Attendance QR display checkpoint 2026-08-25: the monolith already issues `data.qr.token` from `GET /api/v1/organizations/{organizationId}/attendance/me`; FE `PersonalAttendanceHub` now renders that backend token with `QRCodeSVG`, shows loading/empty states, and no longer displays a permanent placeholder. FE typecheck/build and the targeted QR render check pass.

## Verification evidence

- Java 21 is available through Homebrew `openjdk@21`.
- Docker Desktop daemon is available.
- `./mvnw -pl application/monolith-app -am package -DskipTests` completed with `BUILD SUCCESS` on 2026-08-23.
- Project contract tests passed with Testcontainers PostgreSQL: 3 tests, 0 failures, 0 errors.
- Activity contract tests passed with Testcontainers PostgreSQL: 3 tests, 0 failures, 0 errors.
- Identity contract tests passed with Testcontainers PostgreSQL: 10 tests, 0 failures, 0 errors.
- Project organization authorization now consumes the shared `OrganizationDirectory` port; project configuration no longer contains internal project/organization service URLs.
- Work authorization/reporting now consumes `ProjectPermissionChecker` and `OrganizationDirectory`; work configuration no longer contains internal project/organization service URLs.
- Identity project member directory now consumes `ProjectAccessPort` with actor/root context; identity configuration no longer contains a project service URL.
- Shared resource permission filtering and outbox dispatch now use `ProjectPermissionChecker` and `ActivityPublisher`; active backend code has no remaining internal `RestClient` or service URL references.
- Full reactor verification passed after the shared-resource conversion: `./mvnw -pl application/monolith-app -am verify`.
- Monolith bootstrap admin creation is opt-in through `BOOTSTRAP_ADMIN_ENABLED=true`; missing credentials now fail fast when explicitly enabled.
- Database migration operations are documented as backup -> staging restore -> read-only preflight -> reviewed migration, with restore as rollback, in `docs/database/README.md`.
- The attendance `OrganizationClient` compatibility alias was removed after all active production and test references migrated to `OrganizationDirectory`.
- Canonical Flyway baseline initially missed the `organization_permissions` entity table; V1 and new forward migration V7 now create it, including its organization FK and business uniqueness.
- Fresh PostgreSQL 17 executable-JAR smoke startup passed with Flyway V1-V7 and Hibernate `ddl-auto=validate`; final reactor `verify` also passed.
- `MonolithStartupTest` now protects the full Spring context, canonical Flyway schema, PK coverage and required FK validation; focused Testcontainers verification passed with 3 tests, 0 failures, 0 errors.
- Namespace verification passed with zero old references, zero package-path mismatches and zero old Maven group IDs before legacy module removal.
- Local PostgreSQL preflight artifacts before the latest source-only cleanup are historical; the current fresh Testcontainer schema gate is 49 public tables and 80 validated FKs, with zero duplicate-FK, orphan and checked tenant-mismatch failures. The monolith health endpoint is `UP`; `/actuator/info` remains private without an authenticated principal.
- Fresh full verification on 2026-08-24 passed: `./mvnw -pl application/monolith-app -am verify` (all 12 active reactor projects), `npm run typecheck`, `npm run architecture:check`, `npm run lint`, and `npm run build`. Monolith Testcontainers coverage includes seven startup/schema/CRUD tests.
- Schema hardening checkpoint on 2026-08-25: canonical monolith Flyway reaches V22. Fresh PostgreSQL Testcontainers reports 49 public tables and 80 validated FKs; focused and full monolith verification pass. The current schema contract is documented in `docs/database/schema-contract.md`.
- Repository cleanup checkpoint on 2026-08-25: the root Maven build contains only shared modules, domain modules and `application/monolith-app`; CI now validates migration names and monolith boundaries; legacy AppleDouble source metadata and stale frontend cache were moved to Trash.
- Operations hardening checkpoint on 2026-08-25: public Operations controller mutations and reads use typed DTO boundaries, explicit SQL projections replace `select *`, and legacy JDBC maps remain private compatibility internals pending per-resource repository extraction.
- Production checkpoint on 2026-08-25: `application-production.yml` fail-closes Flyway runtime mutation, OpenAPI and actuator detail; production Compose forces the production profile, Redis TLS and loopback binding; the migration runner uses the dedicated migrator role; restore verifies SHA-256 before staging restore.

## Next implementation sequence

The detailed current plans are [Missing domain CRUD and zero fake data](../plans/2026-08-24-missing-domain-crud-and-no-fake-data.md), [Public schema hardening and legacy UUID normalization](../plans/2026-08-24-public-schema-hardening.md), and [Operations hardening](../plans/2026-08-24-operations-hardening.md).

1. Extract the remaining private Operations JDBC compatibility implementation into per-resource typed repositories and validators.
2. Run the FE typecheck, architecture check, lint, build and browser smoke suite against the current monolith API.
3. Supply real production secret-manager values, run the production environment validator, restore a real backup into staging and execute the VPS smoke checklist.
4. Resolve remaining compatibility aliases and permissive Spring flags after duplicate beans/cycles are proven absent.

## Working rules

- Never modify production database data without backup and preflight evidence.
- Preserve unrelated dirty-worktree changes.
- Do not push remote or deploy without explicit user instruction.
- Every new plan is stored under `docs/plans/` and linked from `docs/README.md` when current.
