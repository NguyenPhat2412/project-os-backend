# Phase 0 operations

## Local development

The canonical local Docker stack is `compose.monolith.yaml`. Start PostgreSQL,
Redis, MinIO and Zipkin with `docker compose -f compose.monolith.yaml up -d`.
The monolith can also be run directly with Java 21 through
`scripts/dev/start-backend-dev.sh`; that script reuses the same local
infrastructure Compose file and reloads only the Java process when source code
changes. The canonical database is always PostgreSQL `public`; do not use
generated demo rows.
The local `.env` intentionally contains local bootstrap and port settings. It is not copied to `.env.production`.

## Production deployment

1. Use the repository `.env.production` manifest or copy `.env.production.example` to `/opt/project-os/.env.production`, fill every value, then run `chmod 600 /opt/project-os/.env.production`.
2. Install the Nginx template from `deploy/nginx/project-os.conf.example` and configure a real certificate.
3. Run `DB_URL=... DB_MIGRATOR_USERNAME=project_os_migrator DB_MIGRATOR_PASSWORD=... deploy/release/run-monolith-migrations.sh` against the private database before changing traffic.
4. Start the monolith profile: `docker compose --env-file .env.production -f compose.monolith.prod.yaml up -d`.
5. Confirm `http://127.0.0.1:8080/actuator/health` is `UP`, then run the staging/production smoke test. OpenAPI is disabled in this profile. Runtime Flyway is disabled because migration uses the dedicated migrator role.

The manual release helper is `deploy/release/deploy-image.sh`; it creates the database backup, runs the dedicated migration runner, updates only the immutable monolith image and checks readiness before completing. It does not deploy a gateway or domain-service process.

Before starting production, run `scripts/env/validate-production-env.sh /opt/project-os/.env.production`. It rejects placeholders, localhost database URLs, missing TLS, weak secrets, wildcard CORS, public OpenAPI and insecure cookie/rate-limit settings.

Production must use a managed/private PostgreSQL endpoint with `sslmode=verify-full`. Never publish port 5432 on the VPS. The runtime user must not be a database owner and must not have DDL permissions.

The administrator environment Settings API is read-only in the production profile. Change production values through the secret manager or release pipeline; `PUT /api/v1/admin/environment-config` and its rollback endpoint return `environment_config_read_only`.
SMTP is optional. Keep `EMAIL_WORKER_ENABLED=false` until production SMTP credentials are supplied through the secret manager; never copy the local Gmail app password into production.

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
