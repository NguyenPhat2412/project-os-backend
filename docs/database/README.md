# Database Documentation

Use this directory for PostgreSQL schema ownership, Flyway sequencing, preflight
reports, primary/foreign key policy, tenant integrity, identifier normalization,
backup requirements, and data-quality constraints.

Database plans must never delete or rewrite production data automatically. Every
constraint migration requires an orphan-data report and a verified backup first.

## Controlled migration procedure

1. Create and verify a PostgreSQL custom-format backup with
   `deploy/backup/backup-project-os.sh`. Keep the dump and checksum outside the
   application workspace.
2. Restore that dump into an isolated staging database. Do not use the live
   database for preflight validation.
3. Run the read-only report:

   ```bash
   psql "$STAGING_DATABASE_URL" \
     -v ON_ERROR_STOP=1 \
     -f scripts/db/preflight_public_schema.sql \
     > deploy/postgres/preflight-integrity-$(date -u +%Y%m%dT%H%M%SZ).txt
   ```

4. Review every orphan, tenant mismatch, duplicate key and legacy identifier
   row. A non-zero result must be resolved by an explicit, reviewed data-fix
   procedure; Flyway migrations do not delete or rewrite rows automatically.
5. Start the monolith against the verified staging restore. Flyway baselines an
   existing non-empty schema at version 1 and applies only forward migrations
   V2 through V22. A fresh database runs the canonical V1 baseline first.
6. Run `./mvnw -pl application/monolith-app -am verify`, then smoke-test auth,
   organization, project, attendance, resource and dashboard flows.
7. Take a fresh production backup immediately before the approved migration.
   Rollback is a database restore, never a reverse migration.

The canonical read-only preflight SQL is
`scripts/db/preflight_public_schema.sql`. It is required before and after the
schema-hardening sequence through `V22__feature_position_profiles.sql`.
The current gate expects 49 public tables, one primary key per table and 80
validated foreign keys after V22. The latest migrations also validate ownership
FKs, tenant integrity, data-quality checks, and append-only protection for
activity and organization audit records.
The older `deploy/postgres/preflight-integrity.sql` remains a historical
reference and is not the complete current report.

The current schema contract is documented in `docs/database/schema-contract.md`.
The hardening record is documented in
`docs/plans/2026-08-24-public-schema-hardening.md`. The local verification
artifact is the read-only command output captured on 2026-08-24; no production
database was modified.
