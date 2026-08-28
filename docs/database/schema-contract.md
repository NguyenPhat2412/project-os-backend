# Project OS PostgreSQL schema contract

Status: current canonical contract, 2026-08-27.

## Source of truth

- Database: PostgreSQL database `project_os`.
- Schema: `public`.
- Migration owner: `application/monolith-app/src/main/resources/db/migration/monolith/`.
- Current canonical Flyway version: V29.
- Hibernate mode: `ddl-auto=validate`; Hibernate must not create or alter tables.

## Release gate

A fresh PostgreSQL Testcontainer must complete Flyway V1–V29 and satisfy the
monolith schema assertions: 58 public tables (57 business tables plus
`flyway_schema_history`), 102 validated foreign keys, exactly one primary key
per public table, indexed child-side foreign-key columns, no failed migration
history, and no orphan or tenant-mismatch records in the checked relationships.

These counts are verification gates for the current migration set, not a
permission to modify an existing production database without backup, restore
rehearsal and preflight evidence.

## Identifier and tenant rules

- Core entity identifiers are UUIDs.
- Employee, contract, catalog and other legacy business codes remain text.
- Organization-owned records must carry `organization_id` and use composite
  tenant constraints where both parent and child organization IDs exist.
- Legacy text references are resolved only through explicit mapping tables;
  values are never blindly cast to UUID.
- Historical attendance, leave, compensation and audit data is retained by
  deactivation or status transition, not destructive cascade behavior.

## Relationship coverage

- Canonical domain tables use UUID foreign keys and composite `(id,
  organization_id)` keys where tenant scope is part of the relationship.
- Legacy `enterprise_contracts`, `enterprise_kpi_evaluations` and
  `enterprise_leave_balances` use validated `(employee_uuid,
  organization_uuid)` foreign keys to `employees`.
- `enterprise_teams` uses validated organization and department UUID links,
  including the department tenant boundary.
- The remaining legacy `enterprise_*` snapshot tables intentionally have no
  guessed foreign keys because their owner columns and tenant semantics have
  not been verified. They remain compatibility tables until an explicit
  mapping/owner migration is approved.
- V28–V29 index every child-side foreign-key column set, including composite
  keys in FK column order, to keep relationship checks, joins and parent-row
  operations predictable at scale.

## Database roles

- `project_os_migrator`: Flyway release job only; may perform schema changes.
- `project_os_runtime`: application runtime; no DDL or database ownership.
- `project_os_readonly`: reporting and inspection only.

Production connections use PostgreSQL TLS with `sslmode=verify-full`, and port
5432 remains private. Schema rollback is a database restore, not a reverse
migration.
