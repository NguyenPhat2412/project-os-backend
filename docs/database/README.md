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
   V2 through V29. A fresh database runs the canonical V1 baseline first.
6. Run `./mvnw -pl application/monolith-app -am verify`, then smoke-test auth,
   organization, project, attendance, resource and dashboard flows.
7. Take a fresh production backup immediately before the approved migration.
   Rollback is a database restore, never a reverse migration.

The canonical read-only preflight SQL is
`scripts/db/preflight_public_schema.sql`. It is required before and after the
schema-hardening sequence through `V29__complete_foreign_key_indexes.sql`.
The current gate expects 58 public tables (57 business tables plus the Flyway
history table), one primary key per table and 102 validated foreign keys after
V29. The latest migrations also validate ownership FKs, tenant integrity,
data-quality checks, child-side FK indexes, and append-only protection for
activity and organization audit records.
The older `deploy/postgres/preflight-integrity.sql` remains a historical
reference and is not the complete current report.

The current schema contract is documented in `docs/database/schema-contract.md`.
The hardening record is documented in
`docs/plans/2026-08-24-public-schema-hardening.md`. The local verification
artifact is the read-only command output captured on 2026-08-24; no production
database was modified.

## Local Docker PostgreSQL

The local restore target is the PostgreSQL service from
`compose.monolith.yaml`, database `project_os`, owned by
`project_os_owner`. Keep the real password only in the ignored `be/.env` and
start the local stack with:

```bash
docker compose --env-file .env \
  -f compose.monolith.yaml \
  up -d --build
```

The command preserves the named PostgreSQL volume between restarts. For fast
backend iteration, `scripts/dev/start-backend-dev.sh` starts this same
infrastructure and runs the monolith directly on the host.
