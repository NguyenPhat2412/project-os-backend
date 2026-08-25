# FE–BE Contract Normalization Implementation

**Status:** phase checkpoint completed; schema-dependent HRM expansion pending

## Goal

Move frontend requests to the modular-monolith API contract, preserve dynamic PostgreSQL data, and make unsupported domains fail explicitly until their public-schema owner is verified.

## Implemented

- Standardized `apiClient` mutation unwrapping and preserved paginated `{data, meta}` responses for knowledge collections.
- Added organization-scope resolution for legacy compatibility callers; active network requests resolve to `/api/v1/organizations/{organizationId}/...`.
- Migrated direct FE calls for teams, contracts, leave, training, regulations, KPI, company email, reports, dashboard employee lookup and profile employee sync to scoped monolith routes.
- Removed synthetic employee fallback, fake report creation, fake company-domain configuration, and generated leave request identifiers from the migrated flows.
- Made permission updates namespace-aware: `page:*` and `component:*` replacements no longer delete each other.
- Added project dashboard, workload and allowlisted report read models backed by persisted project resources.
- Added schema-backed read APIs for verified legacy `public.enterprise_*` operations tables. Unknown resources return `operations_resource_not_found`; no tables or demo data are invented.
- Replaced fixed FE `503` placeholders for reports and company emails with backend proxy routes.
- Activity UI now reads the canonical project activity endpoint. Activity writes remain append-only through `ActivityPublisher` and cannot be created from the FE activity screen.

## Verification

- FE `npm run typecheck` — pass.
- FE `npm run architecture:check` — pass.
- FE `npm run lint` — pass with no errors.
- FE `npm run build` — pass.
- Backend `./mvnw -pl application/monolith-app -am verify` — `BUILD SUCCESS`; Java 21, PostgreSQL 17 Testcontainers, Flyway public schema and monolith startup tests pass.

## Schema boundary and next phase

The canonical baseline contains verified enterprise tables for contracts, KPI evaluations, leave balances, teams, catalogs and company profile, but no verified public owner for positions, report definitions, company mailboxes, training courses or regulations. Their CRUD APIs must not be fabricated. Before implementing them, create the required backup/preflight artifact, map the legacy tables, review the migration, then add forward-only tables/FKs and contract tests.

## Rules

- Do not add mock business data or hardcoded tenant values.
- Do not rewrite or delete database data automatically.
- Do not push or deploy.
- Update this plan and `docs/memory/project-os-memory.md` at the next checkpoint.
