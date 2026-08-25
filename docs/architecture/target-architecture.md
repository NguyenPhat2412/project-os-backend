# Project OS target backend architecture

## Runtime model

Project OS is a modular monolith. One Spring Boot process in
`application/monolith-app` exposes the complete `/api/v1/**` contract. Domain
modules are Maven libraries loaded into that process; they are not independently
deployed services.

```text
PostgreSQL public schema
          ▲
          │ typed repositories / Flyway
application/monolith-app
          │ in-process application ports
          ▼
modules/*  ── shared/platform + shared/resource
          ▲
          │ HTTPS REST API
frontend web/mobile clients
```

## Repository layout

```text
project-os/
├── app/                         # React Native / Expo application
├── fe/                          # Next.js frontend
├── be/
│   ├── application/monolith-app # only executable backend
│   ├── modules/                  # domain modules
│   ├── shared/                   # platform and resource infrastructure
│   ├── deploy/                   # image, reverse proxy, backup and release tooling
│   ├── scripts/                  # local and database checks
│   └── docs/                     # current architecture, database and operations docs
├── AGENTS.md                    # three-tier and no-mock-data rules
└── docs/                        # cross-project documentation
```

## Module boundaries

The dependency direction is:

```text
web → application → domain
infrastructure → domain
module A → module B application port
```

Controllers do not call repositories directly. Cross-domain consumers depend on
ports such as `OrganizationDirectory`, `ProjectAccessPort`, `PermissionChecker`
and `ActivityPublisher`. No internal HTTP client or service URL is used in
monolith mode.

## Database contract

- PostgreSQL database `project_os`, schema `public`, is the single source of truth.
- Flyway migrations are canonical under the monolith application and currently end
  at V22.
- Hibernate uses `ddl-auto=validate`; it never creates or changes production schema.
- Production migration runs with `project_os_migrator`; the application uses a
  runtime role without DDL privileges.
- Business data is never generated as a frontend fallback or fake seed.

## API contract

- Collection: `{ data, meta }`.
- Detail and mutation: `{ data }`.
- Delete: `204 No Content`.
- Every organization-owned query includes the organization scope.
- Actor and ownership values come from the authenticated principal and server-side
  authorization, never from untrusted request fields.

## Supported runtime files

- `compose.monolith.yaml` for local infrastructure and the monolith.
- `compose.monolith.prod.yaml` for the application and Redis with managed/private
  PostgreSQL.
- `deploy/release/run-monolith-migrations.sh` for the migration role.
- `deploy/backup/` for backup and guarded restore operations.

Gateway, service-based Compose files, root `src/`, and the manual migration tool
are historical artifacts and are not supported runtime paths.
