# Backend Production Readiness & Security Hardening Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` or `superpowers:subagent-driven-development` to implement this plan task-by-task with verification checkpoints.

**Goal:** Raise the modular monolith from local/staging readiness to production readiness while protecting HRM data and providing a controlled VPS rollout.

**Architecture:** One `application/monolith-app` process serves `/api/v1/**`. Production uses a private/managed PostgreSQL database and a monolith-only Docker Compose runtime behind HTTPS. PostgreSQL `public` remains canonical and the frontend never accesses it directly.

**Operational profile:** Hardened baseline, RPO 24 hours, RTO 4 hours, 30-day backup retention, no production deployment or database mutation during this implementation.

## Execution constraints

- No mock or demo business data.
- No production data change without backup, restore verification and read-only preflight.
- Hibernate remains `ddl-auto=validate`; schema changes are forward-only Flyway migrations.
- Local native PostgreSQL uses `localhost:5432`; production PostgreSQL is private and TLS-protected.
- Legacy gateway/service deployment files remain archival until monolith acceptance passes, but are removed from active production instructions.

## Work phases

1. Reconcile the V1–V16 migration baseline, schema tests and current documentation.
2. Replace the Operations dynamic map contract with typed resource DTOs, application services and repositories while preserving public routes.
3. Harden cookie authentication, CSRF, tenant authorization, internal routes, input limits and login/refresh rate limiting.
4. Add production database roles, TLS configuration, protected object storage and migration-runner boundaries.
5. Replace legacy backup/restore service references with monolith/managed-PostgreSQL runbooks and restore verification.
6. Add monolith-only production Compose, readiness checks, container hardening and HTTPS reverse-proxy configuration.
7. Add structured audit/logging, outbox/backlog metrics, backup alerts and incident response runbooks.
8. Run backend, database, security, image, staging smoke and final production-readiness gates.

## Acceptance gate

The work is complete only when the monolith verification suite passes against V1–V16, Operations endpoints are typed and tenant-scoped, security regression tests pass, backup/restore is proven on staging, active production instructions no longer deploy the gateway/service stack, and the final checklist explicitly records any remaining production limitation.

## Implementation checkpoint — 2026-08-24

Completed in this working session:

- V1–V16 is asserted by `MonolithStartupTest`; public PK/FK/duplicate-FK/schema checks remain active.
- Internal `/api/v1/internal/**` is denied even for an authenticated JWT.
- Production CORS rejects wildcard origins when credentials are enabled; upload limits and readiness probes are configured.
- Operations controllers now expose `OperationsResourceDto` and `ApiResponse`/`PageResponse` instead of returning JDBC maps. The internal JDBC adapter still needs the planned per-resource repository/application-service split before the Operations phase can be marked complete.
- Redis-backed login/refresh rate limiting is implemented and enabled only by the production Compose profile.
- `compose.monolith.prod.yaml` contains only monolith and Redis; PostgreSQL is external/private, runtime Flyway is disabled, and the container is non-root/read-only with dropped capabilities.
- Dedicated migration runner, managed-PostgreSQL backup/restore scripts, checksum artifacts, Nginx HTTPS proxy example, monitoring paths and monolith release workflow are in place.
- Active production workflow and runbook no longer deploy the gateway/domain-service stack.

Still required before the `production-ready` label:

- Prove backup restore on an independent staging database and retain the report/checksum artifacts.
- Split the remaining Operations JDBC adapter into verified per-resource repositories/services and add the complete resource contract matrix.
- Add full security pipeline (dependency/secret/SAST/image/SBOM/ZAP), outbox/backlog alerts and incident-response drill evidence.
- Run the complete release smoke test against staging with the real managed PostgreSQL, Redis and private object storage endpoints.

## Implementation checkpoint — 2026-08-25

- Created the real, Git-ignored deployment file `.env.production` with the complete monolith variable surface: image, runtime/migrator database roles, PostgreSQL TLS, Redis, JWT/session security, CORS, rate limiting, Flyway boundary, storage, upload limits, actuator/OpenAPI policy and backup retention.
- Added `scripts/env/validate-production-env.sh`. It fails closed on placeholders, localhost database URLs, missing `sslmode=verify-full`, weak secrets, wildcard CORS, public OpenAPI, insecure cookies, disabled rate limiting or unsafe file permissions.
- Completed `.env.production.example` with the same variable contract for future installations.
- Removed the production Compose bind mount that exposed the whole environment file inside the application container. Secrets are passed only as required container environment values; the host file remains outside the runtime filesystem.
- The new `.env.production` intentionally does not pass validation yet because real managed PostgreSQL, frontend domain, object-storage credentials, offsite backup remote and tested image SHA are external deployment inputs. No production deployment was attempted.

## Implementation checkpoint — 2026-08-25 — backend folder/import cleanup

- Removed the deprecated attendance `OrganizationClient` compatibility alias from the active source tree.
- Migrated `AttendanceApplicationService`, `AttendanceController` and `AttendanceContractTest` to the shared `OrganizationDirectory` port.
- Added `OrganizationDirectoryAdapterTest` to prevent the monolith adapter from implementing a domain-specific compatibility port.
- Updated `MonolithStartupTest` from V15 to the actual canonical Flyway V16 count/version; focused monolith verification passed 9/9 and the clean monolith compile passed.
- Removed macOS `._*` metadata artifacts from backend `src` and generated `target` trees. No business data or production database was changed.
