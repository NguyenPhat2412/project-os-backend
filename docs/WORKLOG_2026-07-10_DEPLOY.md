# ProjectOS Deployment Worklog - 2026-07-10

## Goal

Deploy the Spring Boot backend to the existing VPS and connect the ProjectOS
frontend to PostgreSQL-backed Project APIs without interrupting current services.

## Source

- Repository: `https://github.com/NguyenPhat2412/project-os-backend`
- Branch: `main`
- Initial backend commit: `2c640c4`
- Repository preparation commit: `0ebaefe`
- Secrets and generated files are excluded by `.gitignore`.

## Verified Before Deployment

- Backend integration tests previously passed: 2 tests, 0 failures.
- Production JAR compiled successfully after environment-driven DB/port changes.
- VPS: Ubuntu 24.04, Nginx active, approximately 6.8 GiB RAM available.
- Existing services remain online:
  - `anti-scam-backend-mobile`
  - `project-os` frontend on port 3000
- Existing frontend still uses Firebase/Next API routes; Spring API connection is pending.

## Planned Production Layout

- Spring Boot: `127.0.0.1:8081`, managed by systemd.
- PostgreSQL: local-only port 5432.
- Public API prefix: `https://project-os.uytinmang.vn/backend/`.
- Nginx continues routing `/` to the existing Next.js frontend.
- Database, JWT and bootstrap credentials are stored in a root-readable environment file on the VPS.

## Deployment Status

`PENDING_PRODUCTION_CONFIRMATION`

## Required Verification

- Backend health returns HTTP 200.
- Admin login returns a JWT.
- Project create/read/update/delete succeeds through the public Nginx URL.
- Project row is confirmed in PostgreSQL before deletion.
- Existing frontend and anti-scam services remain healthy.
- Frontend create-project flow calls the Spring API and survives page reload.

## Rollback

- Restore the timestamped Nginx configuration backup.
- Disable the `project-os-backend` systemd service.
- Point the frontend back to its previous release if frontend integration fails.
- Keep PostgreSQL data intact unless an explicit data rollback is approved.
