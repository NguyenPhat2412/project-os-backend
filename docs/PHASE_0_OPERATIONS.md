# Phase 0 operations

## Local development

Use `docker compose --profile storage-dev up -d --build`. OpenAPI is enabled only by the local Compose default (`OPENAPI_ENABLED=true`); standalone services default it to false.

## Production deployment

1. Copy `.env.production.example` to `/opt/project-os/.env.production`, fill every value, then run `chmod 600 /opt/project-os/.env.production`.
2. Install the Nginx template from `deploy/nginx/project-os.conf.example` and configure TLS at Cloudflare.
3. Copy `deploy/cloudflared/config.yml.example` to the Cloudflare host configuration and replace the tunnel UUID/domain.
4. Start only ProjectOS: `docker compose --env-file .env.production -f compose.yaml -f compose.prod.yaml up -d --build`.
5. Confirm `http://127.0.0.1:18080/actuator/health` is `UP`. OpenAPI is disabled in this profile.

The GitHub deploy workflow is intentionally manual. It requires `DEPLOY_HOST`, `DEPLOY_USER`, and `DEPLOY_SSH_KEY` secrets plus the `DEPLOY_PATH` production environment variable. It changes only that directory.

## Backups and restore

Install `rclone`, configure a Google Drive remote, then set these values in `/etc/project-os/backup.env`:

```sh
BACKUP_DIR=/var/backups/project-os
BACKUP_RETENTION_DAYS=30
RCLONE_REMOTE=gdrive:project-os-backups
MINIO_VOLUME=project-os-platform_minio_data
PROJECT_OS_COMPOSE_FILE=/opt/project-os/compose.yaml
```

Install `deploy/systemd/project-os-backup.service` and `.timer`, then run `systemctl enable --now project-os-backup.timer`. The timer runs at 02:00 UTC and retains 30 days by default.

Restore is deliberately guarded: set `PROJECT_OS_RESTORE_CONFIRM=RESTORE_PROJECT_OS` and pass `postgres.dump` plus the matching optional `minio-data.tar.gz` to `deploy/backup/restore-project-os.sh`. Run `chmod +x deploy/backup/*.sh` on the VPS and test a restore on a non-production host before relying on backups.

## Firestore cutover

The migration tool is ready but requires Firebase application credentials and an approved write freeze. Run `npm ci && npm run dry-run` in `migration-tool`, inspect `migration-report.json`, then run `npm run migrate` twice. The second run must create no records.
