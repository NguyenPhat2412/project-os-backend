# Training and Knowledge Three-Tier Design

## Goal

Move Training, Documents, and Wiki business data out of the frontend `hrmDatabase` and local-only state into PostgreSQL-backed APIs, while preserving organization/project scoping and making server-side pagination and search the default.

## Architecture

Knowledge Service owns project-scoped `training`, `documents`, and `wikis` resources in its PostgreSQL `resource_records` table. The existing generic resource controller remains the single backend interface; approval is represented by a backend patch to the document status and approval metadata. Next.js API routes proxy these endpoints and never create business records locally. Frontend hooks use one paginated knowledge data layer and invalidate/refetch after mutations.

## API contract

- `GET /api/v1/projects/{projectId}/training?page=0&size=25&search=&status=`
- `POST /api/v1/projects/{projectId}/training`
- `PATCH /api/v1/projects/{projectId}/training/{id}`
- `DELETE /api/v1/projects/{projectId}/training/{id}`
- `GET|POST /api/v1/projects/{projectId}/documents`
- `PATCH|DELETE /api/v1/projects/{projectId}/documents/{id}`
- `POST /api/v1/projects/{projectId}/documents/{id}/approve`
- Equivalent CRUD/list endpoints for `wikis`.

Responses use `{ data: [], meta: { page, size, total, totalPages } }` for lists and `{ data: item }` for mutations. Search and filters are evaluated by the backend, not by a client-side full dataset.

## Security

The service enforces authenticated project scope through the existing resource security filter. Frontend role checks are presentation-only; create/update/delete/approve authorization is enforced by the backend.

## Migration and compatibility

No legacy data is copied into PostgreSQL automatically. Empty API results remain empty. Existing frontend routes that previously read `hrmDatabase` will become backend proxies or return an explicit backend-unavailable response; they must not fabricate records.

## Verification

- Backend contract tests cover resource registration, pagination/search parameters, CRUD, approval state transition, and project scope.
- Frontend tests cover loading, empty state, server pagination/search, create/update/approve refetch, and backend error state.
- Maven/typecheck/build are run when the local Java runtime and project dependencies are available.
