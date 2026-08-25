# Backend cleanup inventory — 2026-08-25

## Scope

This inventory records the repository cleanup for the Project OS modular monolith.
It is source-repository work only; it does not modify PostgreSQL data or run a
production migration.

The backend worktree already contained user changes before this cleanup began.
Those changes are preserved. No reset, checkout, rebase, or broad overwrite is
allowed during implementation.

## Canonical runtime

| Area | Canonical location | Decision |
| --- | --- | --- |
| Executable application | `application/monolith-app` | Keep; only production runtime |
| Domain modules | `modules/*` | Keep; library modules only |
| Shared platform | `shared/platform`, `shared/resource` | Keep |
| Database migrations | `application/monolith-app/src/main/resources/db/migration/monolith` | Keep; Flyway V1–V22 |
| Local Compose | `compose.monolith.yaml` | Keep |
| Production Compose | `compose.monolith.prod.yaml` | Keep |
| Migration runner | `deploy/release/run-monolith-migrations.sh` | Keep |

## Legacy cleanup candidates

| Path | Current evidence | Decision | Required guard |
| --- | --- | --- | --- |
| `src/` | Legacy root Spring application; not in canonical Maven modules | Remove | Search active references and retain no runtime dependency |
| `api-gateway/` | Legacy gateway; root POM still lists it | Remove | Remove Maven module and gateway deployment references first |
| `migration-tool/` | Manual Node migration tool containing legacy SQL/seed files | Remove | Confirm Flyway is the only migration path |
| `qa/` | Windows PowerShell smoke scripts superseded by JUnit/Testcontainers | Remove | Preserve relevant checks in automated tests/CI |
| `compose.yaml` | Microservice Compose stack | Remove | Replace active documentation and CI references |
| `compose.prod.yaml` | Microservice production override | Remove | Replace active deployment references |
| `compose.release.yaml` | Microservice image release overlay | Remove | Confirm monolith release workflow is canonical |
| `fe/.next-stale-20260825-1116/` | Generated stale Next.js cache, approximately 989 MB | Remove | Do not touch current `.next/` or source files |
| `._*` and `.DS_Store` | macOS metadata/build noise | Remove | Use explicit repository paths; add ignore rules |

## References found before cleanup

Active references requiring correction include:

- `pom.xml` still lists `api-gateway`.
- `.dockerignore` still allowlists legacy service directories and gateway.
- `README.md` still instructs users to run `compose.yaml` and `compose.prod.yaml`.
- CI still validates the legacy Compose file.
- Memory and naming documentation describes legacy paths as retained artifacts.
- Mobile and historical review documents mention `be/src` or gateway paths and must
  be marked archive-only or updated if they are active guidance.

Archive-only references under `docs/archive`, historical review files, and old
plans are not runtime dependencies. They must not be presented as current deploy
instructions.

## Baseline verification

Command:

```bash
./mvnw -pl application/monolith-app -am verify
```

Baseline result before this implementation:

- Domain module tests completed successfully.
- Flyway applied V1–V22 to the monolith Testcontainer.
- `MonolithStartupTest` failed only on stale schema expectations:
  - expected 48 public tables, actual 49;
  - expected 79 validated foreign keys, actual 80.

The baseline failure is addressed in the release-gate phase before legacy
directories are deleted.

## Deletion policy

Deletion is limited to the explicit paths in this document after reference checks
pass. No database row, migration history, current frontend source, current build
cache, or unrelated user change may be deleted.
