# Project OS Backend Documentation

`be/docs/` is the canonical documentation and planning memory for the backend.
Future plans, architecture changes, decisions, migration notes, and operational
instructions must be recorded here.

## Documentation governance

Use the [root AI brain](../../docs/AI/README.md) for cross-repository context and the [document registry](../../docs/AI/document-registry.md) for ownership and source precedence. This directory owns Backend implementation, API, PostgreSQL/Flyway, operations, plans, memory, decisions, and Backend-specific history.

- Documents under `archive/` are historical only and cannot override current Backend architecture, security, API, or data-integrity guidance.
- New multi-step Backend work starts with a dated plan in `docs/plans/YYYY-MM-DD-<name>.md`.
- Database changes document preflight, migration order, rollback, and verification in `docs/database/`.
- Source code, tests, and runtime evidence are required before a plan or review is marked complete.
- Root ProjectOS architecture and three-tier/no-mock rules override local or vendor guidance.

## Structure

```text
docs/
├── README.md             # This index and documentation rules
├── architecture/         # Current architecture and technical specifications
├── api/                   # OpenAPI and API consumer documentation
├── plans/                # Implementation plans, one file per initiative
├── decisions/            # Architecture Decision Records (ADR)
├── database/             # Schema, Flyway, FK, backup and data-quality notes
├── operations/           # Local, release, deployment and recovery runbooks
├── memory/               # Durable project context and current work state
└── archive/              # Historical documents; never current guidance
```

## Documentation rules

1. Every new multi-step task starts with a plan in `docs/plans/YYYY-MM-DD-<name>.md`.
2. Plans contain goal, scope, files, implementation steps, verification, and status.
3. Update `docs/memory/project-os-memory.md` after an architectural decision or completed phase.
4. Put stable rules in `architecture/`, not in a temporary worklog.
5. Put irreversible or cross-cutting choices in `decisions/`.
6. Database changes must document preflight, migration order, rollback, and verification in `database/`.
7. Historical documents must be moved to `archive/` and clearly marked as historical.

## Current documents

- [Project memory](memory/project-os-memory.md)
- [Backend naming convention](architecture/backend-naming-convention.md)
- [Target backend architecture](architecture/target-architecture.md)
- [Cleanup inventory 2026-08-25](architecture/cleanup-inventory-2026-08-25.md)
- [Database schema contract](database/schema-contract.md)
- [Swagger/OpenAPI guide](api/swagger-openapi.md)
- [Modular monolith design](architecture/2026-08-22-training-knowledge-three-tier-design.md)
- [Training and knowledge implementation plan](plans/2026-08-22-training-knowledge-three-tier.md)
- [Documentation structure plan](plans/2026-08-23-docs-structure-standardization.md)
- [Next monolith implementation plan](plans/2026-08-23-monolith-runtime-and-inprocess-boundaries.md)
- [FE–BE contract normalization implementation](plans/2026-08-23-fe-be-contract-normalization.md)
- [Missing domain CRUD and zero fake data](plans/2026-08-24-missing-domain-crud-and-no-fake-data.md)
- [Public schema hardening and legacy UUID normalization](plans/2026-08-24-public-schema-hardening.md)
- [Operations, outbox and FE facade hardening](plans/2026-08-24-operations-hardening.md)
- [Operations index](operations/README.md)
- [Phase 0 operations](operations/phase-0-operations.md)
- [Production runbook](operations/production-runbook.md)
- [Backend production security hardening](plans/2026-08-24-backend-production-security-hardening.md)
- [Monolith database configuration cleanup](plans/2026-08-27-monolith-database-configuration-cleanup.md)
- [Environment Settings synchronization cleanup](plans/2026-08-28-environment-settings-sync-cleanup.md)
- [Deployment worklog archive](archive/WORKLOG_2026-07-10_DEPLOY.md)

Documents in `archive/` are reference-only and must not override current rules.
