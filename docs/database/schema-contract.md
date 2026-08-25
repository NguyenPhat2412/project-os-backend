# Project OS PostgreSQL schema contract

Status: current canonical contract, 2026-08-25.

## Source of truth

- Database: PostgreSQL database `project_os`.
- Schema: `public`.
- Migration owner: `application/monolith-app/src/main/resources/db/migration/monolith/`.
- Current canonical Flyway version: V22.
- Hibernate mode: `ddl-auto=validate`; Hibernate must not create or alter tables.

## Release gate

A fresh PostgreSQL Testcontainer must complete Flyway V1–V22 and satisfy the
monolith schema assertions: 49 public business tables, 80 validated foreign
keys, exactly one primary key per business table, no failed migration history,
and no orphan or tenant-mismatch records in the checked relationships.

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

## Database roles

- `project_os_migrator`: Flyway release job only; may perform schema changes.
- `project_os_runtime`: application runtime; no DDL or database ownership.
- `project_os_readonly`: reporting and inspection only.

Production connections use PostgreSQL TLS with `sslmode=verify-full`, and port
5432 remains private. Schema rollback is a database restore, not a reverse
migration.
