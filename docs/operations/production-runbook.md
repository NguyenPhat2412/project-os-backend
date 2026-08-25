# Project OS production runbook

## Runtime topology

Only `application/monolith-app` serves `/api/v1/**`. Nginx or Caddy terminates
HTTPS and proxies to loopback port 8080. Production Compose runs the monolith
and Redis; PostgreSQL is managed/private and is not exposed by Compose.

## Release sequence

1. Validate the production environment with `scripts/env/validate-production-env.sh`.
2. Create and verify the PostgreSQL backup before schema work.
3. Restore that backup into an isolated staging database and run preflight.
4. Run `deploy/release/run-monolith-migrations.sh` with the migrator role.
5. Start the immutable monolith image with `compose.monolith.prod.yaml`.
6. Check readiness, then run the staging smoke suite before traffic cutover.
7. Keep the previous image tag for application rollback. Restore the database
   backup for schema rollback.

## Security controls

Production requires TLS for PostgreSQL, Redis and object storage, secure
HttpOnly cookies, explicit CORS origins, CSRF on cookie mutations, Redis login
and refresh rate limits, private object storage and disabled OpenAPI. Actuator
publicly exposes health only. Do not log passwords, tokens, cookies,
authorization headers, compensation details or raw request bodies.

## Recovery

Backups are custom-format PostgreSQL dumps with SHA-256 checksums and at least
30 days retention. Offsite upload is required. Restore requires
`PROJECT_OS_RESTORE_CONFIRM=RESTORE_PROJECT_OS` and a staging target, verifies
the checksum, stops the application, restores the dump and then requires a
smoke test. Targets are RPO 24 hours and RTO 4 hours.

## Incident response

Preserve logs and audit events, isolate the affected runtime, revoke sessions,
rotate compromised secrets, validate database integrity, restore only from a
verified backup when necessary, and record a post-incident review. Never edit
or delete append-only audit/activity events from the frontend.
