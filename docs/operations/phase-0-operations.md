# Phase 0 operations

## Local development

Native macOS PostgreSQL is the default local database at `localhost:5432`.
Start Redis and optional storage with `docker compose -f compose.monolith.yaml up -d redis minio zipkin` and run `application/monolith-app` with Java 21. The canonical database is always PostgreSQL `public`; do not use generated demo rows.

## Production deployment

1. Use the repository `.env.production` manifest or copy `.env.production.example` to `/opt/project-os/.env.production`, fill every value, then run `chmod 600 /opt/project-os/.env.production`.
2. Install the Nginx template from `deploy/nginx/project-os.conf.example` and configure a real certificate.
3. Run `DB_URL=... DB_MIGRATOR_USERNAME=project_os_migrator DB_MIGRATOR_PASSWORD=... deploy/release/run-monolith-migrations.sh` against the private database before changing traffic.
4. Start the monolith profile: `docker compose --env-file .env.production -f compose.monolith.prod.yaml up -d`.
5. Confirm `http://127.0.0.1:8080/actuator/health` is `UP`, then run the staging/production smoke test. OpenAPI is disabled in this profile. Runtime Flyway is disabled because migration uses the dedicated migrator role.

The manual release helper is `deploy/release/deploy-image.sh`; it creates the database backup, runs the dedicated migration runner, updates only the immutable monolith image and checks readiness before completing. It does not deploy a gateway or domain-service process.

Before starting production, run `scripts/env/validate-production-env.sh /opt/project-os/.env.production`. It rejects placeholders, localhost database URLs, missing TLS, weak secrets, wildcard CORS, public OpenAPI and insecure cookie/rate-limit settings.

Production must use a managed/private PostgreSQL endpoint with `sslmode=verify-full`. Never publish port 5432 on the VPS. The runtime user must not be a database owner and must not have DDL permissions.

The GitHub deploy workflow is intentionally manual. It requires `DEPLOY_HOST`, `DEPLOY_USER`, and `DEPLOY_SSH_KEY` secrets plus the `DEPLOY_PATH` production environment variable. It changes only that directory.

## Backups and restore

Install `pg_dump`, `pg_restore` and `rclone`, configure an offsite remote, then set these values in `/etc/project-os/backup.env`:

```sh
BACKUP_DIR=/var/backups/project-os
BACKUP_RETENTION_DAYS=30
RCLONE_REMOTE=offsite:project-os-backups
DB_URL=jdbc:postgresql://private-db.example.internal:5432/project_os?sslmode=verify-full
DB_USERNAME=project_os_runtime
DB_PASSWORD=replace-with-secret
```

Install `deploy/systemd/project-os-backup.service` and `.timer`, then run `systemctl enable --now project-os-backup.timer`. The timer runs at 02:00 UTC and retains 30 days by default.

Restore is deliberately guarded: set `PROJECT_OS_RESTORE_CONFIRM=RESTORE_PROJECT_OS` and `PROJECT_OS_RESTORE_TARGET=staging`, then pass `postgres-public.dump` to `deploy/backup/restore-project-os.sh`. Run `chmod +x deploy/backup/*.sh` on the VPS and test a restore on an independent staging database before relying on backups. The backup target is RPO 24 hours and retention is at least 30 days; the recovery target is RTO 4 hours.

## PostgreSQL migration verification

The current system uses PostgreSQL as its business-data source of truth. Verify the applicable Flyway migrations and PostgreSQL backup/restore process before release. Historical Firebase migration instructions are archived and must not be used for current deployments.
