# Firestore to Project OS migration

The tool is dry-run by default. It reads Firestore, reports counts, and performs no PostgreSQL or API writes.

1. Copy `.env.example` to a secret file outside the repository and load those variables in the shell.
2. Run `npm ci`.
3. Run `npm run dry-run` and review `migration-report.json`.
4. During the approved write freeze, run `npm run migrate`.
5. Run `npm run migrate` a second time. Every record must be reported as skipped, with no duplicates.

`--apply` writes business data only through `/api/v1`. The direct PostgreSQL connection is used solely for the
`migration.legacy_mappings` and `migration.runs` coordination tables. Existing bcrypt hashes are preserved; Google-only
accounts remain without a password. Set `MIGRATION_DEFAULT_PASSWORD` only for legacy password users that have no bcrypt
hash and must be assigned a temporary password.
