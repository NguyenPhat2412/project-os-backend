# Environment Contract Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or superpowers:subagent-driven-development) to implement this plan task-by-task with verification checkpoints.

**Goal:** Keep exactly two Compose entrypoints—local and production—with one explicit runtime environment contract without copying local infrastructure settings into production.

**Architecture:** `monolith-app` consumes canonical runtime names (`DB_*`, `REDIS_*`, `OBJECT_STORAGE_*`, `SMTP_*`, `NINEROUTER_*`). Compose-only bootstrap names remain limited to the infrastructure they configure (`POSTGRES_*`, `MINIO_ROOT_*`, ports, backup and image settings). Local and production environment files remain separate because their security and topology requirements differ.

**Tech Stack:** Docker Compose, Spring Boot environment binding, PostgreSQL, Redis, MinIO/object storage, SMTP, shell contract checks.

**Spec:** User request to synchronize `be/.env`, `be/.env.production`, and `compose.monolith*` while reducing duplicate and legacy configuration.

## Global Constraints

- PostgreSQL `public` remains the only database schema.
- `application/monolith-app` remains the only production runtime.
- Never print, commit, or rewrite secret values.
- Do not copy local SMTP, JWT, database, or storage credentials into production.
- `POSTGRES_PASSWORD` is local PostgreSQL bootstrap configuration, not an administrator runtime setting.
- `MINIO_ROOT_*` is local MinIO bootstrap configuration; runtime storage uses `OBJECT_STORAGE_*`.
- No database migration, production deploy, or production database mutation.
- Preserve unrelated uncommitted work.

## Contract decisions

Runtime names passed to the application:

```text
DB_URL DB_USERNAME DB_PASSWORD DB_POOL_SIZE
REDIS_HOST REDIS_PORT REDIS_PASSWORD REDIS_SSL
OBJECT_STORAGE_ENDPOINT OBJECT_STORAGE_ACCESS_KEY OBJECT_STORAGE_SECRET_KEY OBJECT_STORAGE_BUCKET
SMTP_USERNAME SMTP_PASSWORD SMTP_CONNECT_TIMEOUT_MS SMTP_TIMEOUT_MS EMAIL_WORKER_ENABLED
NINEROUTER_URL NINEROUTER_KEY NINEROUTER_CONNECT_TIMEOUT NINEROUTER_READ_TIMEOUT
```

Local-only infrastructure names:

```text
POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD POSTGRES_HOST_PORT
MINIO_ROOT_USER MINIO_ROOT_PASSWORD MINIO_API_PORT MINIO_CONSOLE_PORT
ZIPKIN_PORT
```

Production-only deployment names:

```text
PROJECT_OS_MONOLITH_IMAGE DB_MIGRATOR_USERNAME DB_MIGRATOR_PASSWORD
BACKUP_DIR BACKUP_RETENTION_DAYS RCLONE_REMOTE ZIPKIN_ENDPOINT
```

### Task 1: Align local Compose with the runtime contract

**Files:**

- Modify: `be/compose.monolith.yaml`
- Modify: `be/.env.example`

- [ ] Pass `OBJECT_STORAGE_*` directly to `monolith-app`, while retaining `MINIO_ROOT_*` only for the MinIO container.
- [ ] Use `NINEROUTER_URL` for host-side Java and the fixed local container topology; remove the extra `NINEROUTER_CONTAINER_URL` indirection.
- [ ] Pass the full Email/SMTP runtime contract and safe defaults to the local app.
- [ ] Keep PostgreSQL bootstrap variables only on the `postgres` service.
- [ ] Keep the local stack self-contained so it can be started with one Compose file.

### Task 2: Align production Compose with the runtime contract

**Files:**

- Modify: `be/compose.monolith.prod.yaml`
- Modify: `be/.env.production.example`
- Modify: `be/.env.production` only for non-secret disabled/default keys when absent

- [ ] Pass `EMAIL_WORKER_ENABLED`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and SMTP timeout values to `monolith-app`.
- [ ] Pass `NINEROUTER_CONNECT_TIMEOUT` and `NINEROUTER_READ_TIMEOUT`.
- [ ] Continue mapping `OBJECT_STORAGE_*` to the application’s existing storage binding without exposing `MINIO_ROOT_*` in production.
- [ ] Keep production Flyway disabled because migrations run through the separate migrator job.
- [ ] Do not place local credentials or example placeholders into the real production file.

### Task 3: Strengthen the environment contract guard

**Files:**

- Modify: `be/scripts/env/check-settings-contract.sh`
- Create: `be/scripts/env/check-runtime-contract.sh`

- [ ] Verify every application runtime variable referenced by Compose is either canonical or an explicitly documented deployment variable.
- [ ] Fail if `NINEROUTER_CONTAINER_URL` or legacy `S3_*`, gateway, frontend, or domain database passwords are active.
- [ ] Fail if production Compose omits required runtime bindings or exposes local bootstrap names to the application.
- [ ] Check key presence only; never print values.

### Task 4: Update operational documentation

**Files:**

- Modify: `be/docs/operations/phase-0-operations.md`
- Modify: `be/docs/memory/project-os-memory.md`

- [ ] Document the two supported Compose entrypoints: local Docker PostgreSQL and production private PostgreSQL.
- [ ] Document that `.env` and `.env.production` are intentionally different and must not be merged.
- [ ] Document the direct local Compose command without an override layer.
- [ ] Document SMTP as optional and disabled until credentials are configured.

### Task 5: Verification

- [ ] Run the runtime contract guards.
- [ ] Run `docker compose -f compose.monolith.yaml config --quiet`.
- [ ] Run `docker compose -f compose.monolith.yaml config --quiet`.
- [ ] Run production Compose config with `.env.production.example` without printing rendered values.
- [ ] Run backend monolith verification and frontend typecheck/build if application bindings changed.
- [ ] Run `git diff --check` for both repositories.

## Rollback

Rollback is a source change revert through review plus application image rollback. No database rollback or secret rollback is performed by this synchronization task.
