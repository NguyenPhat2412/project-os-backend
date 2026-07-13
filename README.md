# ProjectOS backend platform

ProjectOS runs as a Maven multi-module Spring Boot platform:

- `api-gateway` on `127.0.0.1:18080`
- `identity-service`, `project-service`, `work-service`, `operations-service`, `knowledge-service`, `activity-service`
- PostgreSQL 17 with service-owned schemas
- Redis for Gateway read-model caching and MinIO for attachment storage

## Local development

1. Copy `.env.example` to `.env` and replace every placeholder with local-only secrets.
2. Start the stack:

   ```powershell
   docker compose --profile storage-dev up -d --build
   ```

3. Verify:

   ```powershell
   Invoke-RestMethod http://127.0.0.1:18080/actuator/health
   docker compose ps
   ```

Local endpoints:

| Service | Address |
| --- | --- |
| Gateway | `http://127.0.0.1:18080` |
| PostgreSQL | `127.0.0.1:15433`, database `project_os` |
| MinIO console | `http://127.0.0.1:19001` |
| Zipkin traces | `http://127.0.0.1:19411` |

OpenAPI is enabled for local Compose only. Start a standalone service with `OPENAPI_ENABLED=true` when documentation is required.

Run backend verification with:

```powershell
.\mvnw.cmd -q test
```

## Database ownership

Each service owns one PostgreSQL schema and its Flyway history: `identity`, `project`, `work`, `operations`, `knowledge`, and `activity`. Hibernate validates schemas only; it does not create or alter production tables.

## Production

Use the explicit production override; it disables OpenAPI, requires production secrets, enables secure cookies, and keeps database/gateway ports bound to loopback:

```bash
docker compose --env-file .env.production -f compose.yaml -f compose.prod.yaml up -d --build
```

Read [Phase 0 operations](docs/PHASE_0_OPERATIONS.md) before deployment. It covers Nginx/Cloudflare, backups, restore, CI/CD secrets, and the Firestore cutover.

## Firestore migration

`migration-tool` reads Firestore in dry-run mode by default. During an approved write freeze, run its dry-run, inspect the report, apply once, then apply a second time to prove idempotency. It requires Firebase application credentials and is documented in [migration-tool/README.md](migration-tool/README.md).
