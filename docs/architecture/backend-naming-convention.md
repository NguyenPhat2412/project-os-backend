# Project OS Backend Naming Convention

## Runtime

- `application/monolith-app` is the only executable Spring Boot application.
- Domain code is a module, not a deployable service.
- A domain module must not add a second application entrypoint to the monolith runtime.
- Public routes remain under `/api/v1/**`.

## Repository directories

```text
application/monolith-app
modules/identity
modules/organization
modules/attendance
modules/project
modules/work
modules/operations
modules/knowledge
modules/activity
shared/platform
shared/resource
infrastructure/
migration/
```

Use lowercase kebab-case for directories and Maven artifact IDs. Domain modules do
not use the suffix `-service`.

## Java packages and layers

Packages use `com.projectos.backend.<module>` and classes use PascalCase.

```text
<module>/
├── domain/          # entities, value objects, repositories, domain rules
├── application/    # use cases and cross-module ports
├── web/             # REST controllers and API DTOs
├── integration/    # external adapters only
└── infrastructure/  # persistence and technical configuration
```

The allowed dependency direction is:

```text
web → application → domain
infrastructure → domain
module A → module B application port
```

Controllers do not call repositories directly. A module does not access another
module's repository or call another module over HTTP when running in monolith mode.

## Class names

- Use `Employee`, `Project`, and `AttendanceRecord` for domain entities.
- Use `EmployeeApplicationService` for use cases.
- Use `OrganizationDirectory`, `ProjectAccessPort`, and `ActivityPublisher` for module interfaces.
- Use `JpaOrganizationDirectory` or `DefaultActivityPublisher` for implementations.
- Do not introduce `*RestClient`, `*ServiceClient`, or `Internal*Service` for in-process calls.

## Database and API names

- PostgreSQL tables and columns use `snake_case`: `attendance_records`, `organization_id`.
- Primary and foreign keys use `<entity>_id`.
- Constraints use `fk_`, `uq_`, `ck_`, and `idx_` prefixes.
- REST resources use plural nouns: `/employees`, `/projects`, `/leave-requests`.
- JSON fields use lower camelCase.

## Legacy directories

The root `src/`, `api-gateway/`, `migration-tool/`, `qa/`, and service-based Compose
files are not part of the supported backend tree. They are removed after the
cleanup inventory confirms that no active build, deployment or documentation path
depends on them. Historical references belong under `docs/archive/` and must not
be used as current runtime instructions.
