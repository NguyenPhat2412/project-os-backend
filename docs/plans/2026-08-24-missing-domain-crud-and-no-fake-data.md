# Missing domain CRUD and zero fake business data

Status: implementation checkpoint

## Scope

The canonical monolith now owns CRUD endpoints for organization positions, training courses, company regulations, company mailboxes, report definitions, contracts, KPI evaluations, leave balances, teams and global master catalogs. Organization-scoped resources use `/api/v1/organizations/{organizationId}/...` and persist to PostgreSQL `public`. Mutations return persisted data; deletes return `204 No Content`.

## Database policy

Migration `V8__add_missing_domain_owners.sql` creates empty owner tables and indexes only. It does not insert demo organizations, employees, courses, regulations, mailboxes, reports, or seed rows. New tables use UUID identifiers, organization foreign keys, tenant-safe department/employee composite foreign keys, uniqueness rules and non-negative/date checks.

The follow-up schema hardening sequence is recorded in
[Public schema hardening and legacy UUID normalization](2026-08-24-public-schema-hardening.md).
It applies V9 through V12 to remove duplicate FKs, add missing ownership and
tenant constraints, and add verified UUID compatibility columns for legacy
varchar references without deleting or rewriting the original business-code
  columns.

V12 adds `enterprise_leave_balances.employee_uuid`, backfills it only through
verified employee-code mappings, and enforces a tenant-safe FK. No leave
history is deleted.

V13 adds the empty tenant-owned `organization_branches` table for the active
branch and GPS flows. It does not insert branch rows or demo coordinates.

The old standalone organization-module seed migrations are retired to no-op files for fresh module test schemas. The monolith does not load those module migration locations. Existing production data must be handled through backup, preflight and an explicit cleanup decision; no broad destructive cleanup is performed automatically.

## API owners

- `organization`: JPA `Position` aggregate and nested position routes.
- `operations`: JDBC application service with a typed mutation DTO for both new owner tables and verified legacy Operations tables. Legacy identifiers remain compatible with existing text primary keys while all employee, department and organization references are checked against the active tenant.
- `monolith-app`: one Flyway location and one runtime process.

Company profile remains read-only through its legacy global table because it has no verified organization owner. Company-domain configuration, SMTP templates/logs and training attendees remain separate until their database owners are verified. No mock rows, fallback business names or demo employees are created.

## Frontend rules applied

- Training and regulations consume the organization-scoped CRUD responses.
- Reports delete through the persisted resource route.
- Company mailbox UI consumes the mailbox collection; unsupported domain/template/log panels are not populated with synthetic records.
- Department selectors use live organization departments; hardcoded department option lists and company-policy fallback values were removed.
- Position-role seed profiles and tenant/domain defaults are not created in the browser.

## Verification

Required before completion:

```text
./mvnw -pl application/monolith-app -am verify
npm run typecheck
npm run architecture:check
npm run lint
npm run build
```

Targeted contract tests cover position CRUD and persisted training/regulation/mailbox/report CRUD. Test fixtures create their referenced user rows explicitly so database foreign keys are exercised instead of bypassed.
