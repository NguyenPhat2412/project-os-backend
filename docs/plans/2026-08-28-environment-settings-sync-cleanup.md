# Environment Settings Synchronization Cleanup

## Goal

Align the administrator environment settings with the modular-monolith runtime contract. The settings API must not expose gateway, frontend, local Compose, legacy S3, or obsolete AI-provider configuration. Production configuration is view-only.

## Decisions

- PostgreSQL `public` and the monolith remain the runtime source of truth.
- `POSTGRES_PASSWORD` remains a local Compose bootstrap variable; runtime database access uses `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
- Frontend API/WebSocket variables remain in `fe/.env` but are not managed by the backend administrator Settings screen.
- Object storage uses the provider-neutral `OBJECT_STORAGE_*` contract; `MINIO_ROOT_*` is local infrastructure only.
- Google OAuth remains optional because the backend still supports it.
- Production rejects environment update and rollback requests with a stable `environment_config_read_only` error.
- No database migration, production deployment, or secret rotation is performed by this cleanup.

## Implementation checkpoints

1. Create one backend catalog for allowed, secret, URL, and numeric environment keys; make snapshot/validation use it.
2. Remove legacy keys from backend response mapping and enforce production read-only capabilities.
3. Update monolith storage binding to accept `OBJECT_STORAGE_*` while retaining local `MINIO_*` compatibility.
4. Remove legacy fields from frontend environment types, mapping, UI, and additional-field registry.
5. Remove obsolete environment entries and unused frontend S3 adapter references without changing required FE runtime variables.
6. Add contract tests and static guards, then run backend, frontend, and Compose verification.

## Safety

- Preserve all pre-existing uncommitted changes.
- Edit `.env` only by removing explicitly obsolete key lines; never print or rewrite secret values.
- Do not modify PostgreSQL data or run Flyway against production.
