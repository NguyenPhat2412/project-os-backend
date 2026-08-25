# Public schema hardening and legacy UUID normalization

Status: implemented on local PostgreSQL

## Scope

The canonical monolith database remains PostgreSQL `public`. This phase applies
forward-only migrations V8 through V11, removes duplicate foreign keys, adds
missing ownership constraints, and introduces verified UUID reference columns
for legacy text identifiers without deleting the original business-code fields.

## Migration order

- `V8__add_missing_domain_owners.sql`: creates empty owner tables for positions,
  training courses, regulations, company mailboxes and report definitions.
- `V9__harden_public_foreign_keys.sql`: removes duplicate/redundant FK
  constraints, adds missing core FKs, and adds supporting indexes.
- `V10__normalize_legacy_uuid_references.sql`: adds UUID compatibility columns,
  backfills RFC-shaped UUIDs and explicit mapping-table values, and adds FKs.
- `V11__complete_legacy_uuid_backfill.sql`: accepts verified PostgreSQL UUID
  syntax even when legacy values use synthetic version/variant nibbles.

Original legacy text columns remain unchanged. Non-UUID business codes must be
resolved through `enterprise_identifier_mappings`; unresolved values are not
guessed or rewritten.

## Safety and rollback

- A custom-format backup was created before local migration at
  `/tmp/project-os-public-schema-pre-v8-v10.dump`.
- Rollback is database restore, not reverse migration.
- No business rows were deleted. New owner tables contain no demo rows.
- The read-only preflight is
  `scripts/db/preflight_public_schema.sql`.
- The final V11 preflight artifact is
  `/tmp/project-os-preflight-public-schema-20260824-v11.txt`.

## Local evidence

- Local PostgreSQL migrated from Flyway V7 to V11.
- `public` now contains 43 tables: 42 business tables plus
  `flyway_schema_history`.
- 53 foreign keys are validated; no duplicate identical FK pairs remain.
- PK, orphan, tenant mismatch, unresolved legacy-reference and duplicate
  business-identifier checks all returned zero violations.
- Monolith health endpoint returned `UP` after restart.

## Required production procedure

Run the same preflight against a restored production backup, review all output,
take a fresh production backup, then start the monolith so Flyway applies V8–V11.
Do not run broad cleanup or delete unresolved legacy rows automatically.
