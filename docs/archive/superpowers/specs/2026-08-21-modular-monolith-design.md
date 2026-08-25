# Project OS Modular Monolith Design

**Date:** 2026-08-21  
**Status:** Approved direction  
**Scope:** Backend runtime consolidation

## Goal

Run Project OS as one production backend process while preserving domain boundaries from the current `*-service` modules. The modular monolith becomes the only supported runtime and the legacy `be/src` application is removed from the build and deployment path.

## Decisions

1. `monolith-service` is the only application entry point.
2. The current domain modules remain source modules: identity, organization, attendance, project, work, operations, knowledge, activity, resource core, and platform common.
3. `api-gateway` is not required at runtime. The monolith owns the public `/api/v1/**` contract.
4. `be/src` is legacy and must not be compiled, packaged, or deployed.
5. PostgreSQL remains the single source of truth. The monolith uses one database connection and one explicit schema strategy.
6. Hibernate must use `ddl-auto: validate`; schema changes are Flyway migrations only.
7. In-process domain calls replace HTTP calls between modules. HTTP adapters may remain only for an explicitly documented external integration.
8. Authentication and authorization are enforced once by the monolith security chain. Internal endpoints are not public trust boundaries.
9. Outbox records remain transactional with domain writes. Dispatch uses an in-process publisher/handler boundary and must be retryable.

## Target Architecture

```text
Frontend :3000
    |
    v
monolith-service :8080
    |-- public web/API controllers
    |-- one security filter chain
    |-- application services by domain
    |-- in-process domain ports/adapters
    |-- one transaction manager
    |-- Flyway migrations
    v
PostgreSQL project_os
    |-- identity schema
    |-- organization schema
    |-- attendance schema
    |-- project schema
    |-- work schema
    |-- operations schema
    |-- knowledge schema
    |-- activity schema
```

Domain modules may depend on `platform-common` and narrowly defined domain ports. They must not depend on controllers, Spring filter configuration, or another module's repositories. Cross-domain reads/writes use application-facing interfaces such as `OrganizationDirectory` or `ProjectAccessPort` implemented by the owning module.

## Runtime and API

- Build artifact: `monolith-service` executable jar.
- Port: `8080` by default; local frontend continues to use `http://localhost:3000`.
- Public routes retain the existing `/api/v1` paths.
- Gateway-only read-model routes move into monolith-owned controllers or a monolith read-model package.
- Service-specific health and OpenAPI endpoints are consolidated under the monolith.
- No public route may depend on a downstream service URL.

## Database and Migration

The target database is the schema-based PostgreSQL layout currently used by the new modules. Each domain keeps ownership of its schema, but Flyway execution is centralized for the monolith so startup has one ordered migration process.

Migration rules:

- Keep existing domain tables and data where compatible.
- Create a monolith migration location with deterministic ordering, or explicitly configure multiple locations with a documented ordering and ownership rule.
- Do not run the old `be/src/main/resources/db/migration/V1__initial_schema.sql` in the target database.
- Do not run `deploy/postgres/hrm_master_seed.sql` against the production schema unless it is converted into an idempotent, versioned migration for the owning domain.
- Replace `ddl-auto: update` and disabled Flyway in `monolith-service` with `ddl-auto: validate` and enabled Flyway.
- Keep database credentials externalized and fail startup when required production credentials are absent.

The migration plan must include a preflight query for existing schemas/tables, a backup, a dry-run against a restored copy, and a rollback procedure based on restore rather than reverse migrations.

## Authentication and Authorization

- `MonolithSecurityConfig` is the sole `SecurityFilterChain`.
- Identity application services remain the owner of users, refresh tokens, OAuth linking, and profile data.
- Cookie authentication keeps double-submit CSRF protection.
- JWT secret, cookie security, OAuth credentials, bootstrap admin credentials, and CORS origins are required configuration in production.
- `/api/v1/internal/**` must not be globally public merely because the process is monolithic. Internal controller methods must either be removed, made package-private application calls, or protected by a dedicated internal authorization rule that cannot be reached from the public browser contract.
- Organization/project/resource permission checks must execute in-process and must use the authenticated principal, not user-supplied identity fields.

## Inter-Module Communication

Replace the following classes of internal HTTP calls:

- identity to project directory lookup;
- project/work/operations/knowledge/activity permission checks;
- attendance to organization employee/timezone lookup;
- outbox dispatch to activity;
- gateway dashboard aggregation to identity/project/work/operations.

Each replacement must define a small interface in the consuming application package and an implementation backed by the owning module's application service. The implementation must not expose repositories across modules. Calls that were previously independently committed must be reviewed for transaction semantics before being joined into one transaction.

## Caching and Events

- Redis is optional for correctness and may remain as a performance cache for workspace/read models.
- Cache keys include tenant/workspace and authenticated subject where authorization affects the result.
- Cache invalidation occurs after successful transaction commit.
- Outbox rows are written in the same transaction as the domain mutation.
- Dispatcher retry state, backoff, and dead-letter visibility must be observable.

## Removal and Compatibility

- Remove the root legacy application under `be/src` from all build and deployment paths.
- Remove `allow-bean-definition-overriding` and `allow-circular-references` after duplicate beans and cycles are resolved.
- Do not package `api-gateway` in the monolith image.
- Keep a temporary compatibility profile only if a migration client needs old routes; it must be time-bounded and documented.
- Update Compose, Docker, health checks, Postman collection, frontend API base URL, and operational docs to target the monolith.

## Testing and Acceptance

The monolith is accepted only when:

1. `./mvnw -pl monolith-service -am verify` passes with a real Java 21 runtime.
2. A clean PostgreSQL Testcontainer starts from migrations with `ddl-auto=validate`.
3. Authentication, refresh, logout, CSRF, and OAuth-disabled flows pass.
4. Organization, employee, attendance, project, task, knowledge, activity, and dashboard flows pass through one process.
5. Cross-tenant access tests return 403/404 as appropriate.
6. A failed transaction leaves no partial cross-domain write.
7. Outbox rows are created atomically and dispatcher retry behavior is tested.
8. No request path calls an internal service URL in monolith mode.
9. No production configuration contains a default password, JWT secret, or internal token.
10. A restored backup can start the monolith and serve a smoke-test request.

## Rollout

1. Build and test monolith in parallel with the current local runtime.
2. Restore a database backup into a staging database and run the migration preflight.
3. Run contract and smoke tests against the monolith on `localhost:8080`.
4. Point frontend/API clients to the monolith without changing public endpoint paths.
5. Monitor authentication failures, database errors, latency, outbox backlog, and 5xx responses.
6. Keep the previous deployment artifact available for restore; rollback is traffic/config rollback plus database restore if a write migration is not backward compatible.

