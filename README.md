# ProjectOS backend platform

ProjectOS backend is organized as a Maven modular monolith:

- `application/monolith-app` is the only executable application on `127.0.0.1:8080`
- `modules/*` contains identity, organization, attendance, project, work, operations, knowledge, and activity domains
- `shared/*` contains reusable platform and resource infrastructure
- PostgreSQL `public` is the canonical schema
- Redis and MinIO are infrastructure dependencies

## Local development

1. Copy `.env.example` to `.env` and replace every placeholder with local-only secrets.
2. Start only the infrastructure containers and run the monolith directly:

   ```powershell
   bash scripts/dev/start-backend-dev.sh
   ```

   This starts PostgreSQL, Redis, MinIO, and Zipkin in Docker. The backend itself runs
   directly from the working tree on `127.0.0.1:8080`. The development script watches
   Java/YAML source files, recompiles, and restarts only the Java process when code
   changes; it does not rebuild or recreate the backend container. This watcher is
   intentionally used instead of DevTools because the monolith has package-private
   repository interfaces that are incompatible with DevTools' restart classloader.

   For a production-like Docker run, use `compose.monolith.yaml` explicitly:

   ```powershell
   docker compose --env-file .env -f compose.monolith.yaml up -d --build
   ```

3. Verify:

   ```powershell
   Invoke-RestMethod http://127.0.0.1:18080/actuator/health
   docker compose ps
   ```

Local endpoints:

| Service | Address |
| --- | --- |
| Monolith | `http://127.0.0.1:8080` |
| PostgreSQL | `127.0.0.1:15433`, database `project_os` |
| MinIO console | `http://127.0.0.1:19001` |
| Zipkin traces | `http://127.0.0.1:19411` |

OpenAPI is enabled for local Compose only. Start a standalone service with `OPENAPI_ENABLED=true` when documentation is required.

Run backend verification with:

```powershell
.\mvnw.cmd -q test
```

## Backend naming and module ownership

See [backend naming convention](docs/architecture/backend-naming-convention.md). Each domain module owns its entities, application services, controllers, and repositories. Cross-module calls use application interfaces, not HTTP clients or another module's repositories.

Hibernate validates schemas only; it does not create or alter production tables. Flyway migrations for the monolith live under `application/monolith-app/src/main/resources/db/migration/`.

## Production

The modular monolith at `127.0.0.1:8080` is the only application entrypoint.
Nginx/Caddy may sit in front of it in production; no gateway or downstream domain
service process is deployed.

Use the explicit production Compose file; it disables OpenAPI, requires production
secrets, enables secure cookies, and keeps the application bound to loopback:

```bash
docker compose --env-file .env.production -f compose.monolith.prod.yaml up -d
```

Read [Phase 0 operations](docs/operations/phase-0-operations.md) before deployment. It covers reverse proxy, backups, restore, CI/CD secrets and PostgreSQL migration verification.

## Migration policy

Flyway migrations under `application/monolith-app/src/main/resources/db/migration/monolith/`
are the only supported schema migration path. Manual SQL migration tools and
service-based deployment stacks are not part of the backend repository runtime.
