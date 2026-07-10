# ProjectOS Backend

REST API for ProjectOS, built with Spring Boot and PostgreSQL. The current
backend provides user registration, JWT authentication, role-based access
control, and persistent Project CRUD operations.

## Technology Stack

- Java 21
- Spring Boot 4
- Spring Security and JWT
- Spring Data JPA and Hibernate
- PostgreSQL 17
- Flyway database migrations
- Maven Wrapper
- JUnit and MockMvc integration tests

## Current Features

- Register and authenticate users with email and password.
- Hash passwords with BCrypt.
- Issue stateless JWT access tokens.
- Assign `USER` to every public registration.
- Bootstrap an initial `ADMIN` account through environment variables.
- Allow authenticated users to read projects.
- Restrict project creation, updates, and deletion to administrators.
- Validate database schema through Flyway and Hibernate.
- Return structured JSON validation and API errors.

## Prerequisites

Install the following tools before running the project:

- JDK 21
- Docker Desktop or a PostgreSQL server
- Git

You do not need to install Maven globally. Use `mvnw.cmd` on Windows or
`./mvnw` on Linux and macOS.

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/NguyenPhat2412/project-os-backend.git
cd project-os-backend
```

### 2. Start PostgreSQL

The included Compose file starts PostgreSQL on `127.0.0.1:5433`:

```bash
docker compose up -d
docker compose ps
```

The default local database configuration is:

| Setting | Value |
| --- | --- |
| Database | `project_os` |
| Username | `project_os` |
| Password | `local_project_os_password` |
| Host port | `5433` |

These values are for local development only.

### 3. Configure environment variables

Spring Boot does not load `.env` files automatically. Set the variables in
your terminal or IntelliJ run configuration.

Windows PowerShell:

```powershell
$env:DB_PASSWORD='local_project_os_password'
$env:JWT_SECRET='replace-this-with-at-least-32-random-characters'
$env:BOOTSTRAP_ADMIN_EMAIL='admin@example.com'
$env:BOOTSTRAP_ADMIN_PASSWORD='replace-this-admin-password'
$env:BOOTSTRAP_ADMIN_NAME='Administrator'
```

Linux or macOS:

```bash
export DB_PASSWORD='local_project_os_password'
export JWT_SECRET='replace-this-with-at-least-32-random-characters'
export BOOTSTRAP_ADMIN_EMAIL='admin@example.com'
export BOOTSTRAP_ADMIN_PASSWORD='replace-this-admin-password'
export BOOTSTRAP_ADMIN_NAME='Administrator'
```

The JWT secret must contain at least 32 bytes. Bootstrap passwords must contain
between 8 and 72 characters.

### 4. Run the application

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux or macOS:

```bash
./mvnw spring-boot:run
```

The backend listens on `http://127.0.0.1:8081` by default.

Verify the application:

```bash
curl http://127.0.0.1:8081/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Running with IntelliJ IDEA

1. Open the `project-os-backend` directory.
2. Wait for IntelliJ to import `pom.xml` and download dependencies.
3. Open **Run > Edit Configurations**.
4. Select `ProjectOsBackendApplication`.
5. Add the environment variables from the Quick Start section.
6. Run `ProjectOsBackendApplication`.
7. Wait for `Tomcat started on port 8081` in the Run window.

Do not start a second application process while port `8081` is already in use.

## Configuration Reference

Use `.env.example` as a reference. Do not commit real environment files.

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `DB_PASSWORD` | Yes | None | PostgreSQL password |
| `JWT_SECRET` | Yes | None | HMAC secret, minimum 32 bytes |
| `DB_URL` | No | `jdbc:postgresql://localhost:5433/project_os` | JDBC connection URL |
| `DB_USERNAME` | No | `project_os` | PostgreSQL username |
| `SERVER_ADDRESS` | No | `127.0.0.1` | Address used by Spring Boot |
| `SERVER_PORT` | No | `8081` | HTTP port |
| `JWT_TTL_HOURS` | No | `8` | Access-token lifetime |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000` | Comma-separated frontend origins |
| `BOOTSTRAP_ADMIN_EMAIL` | No | Empty | Initial administrator email |
| `BOOTSTRAP_ADMIN_PASSWORD` | No | Empty | Initial administrator password |
| `BOOTSTRAP_ADMIN_NAME` | No | `Administrator` | Initial administrator name |

The bootstrap process runs only when both admin email and password are set. If
the email already exists, that account is promoted to `ADMIN`. Remove the
bootstrap email and password from production configuration after the initial
administrator has been created.

## Authentication

Public registration always creates a `USER`. Clients authenticate by sending
the JWT returned from registration or login:

```http
Authorization: Bearer <access-token>
```

JWT access tokens expire after eight hours by default. Refresh tokens and
password-reset flows are not implemented yet.

### Register a user

```bash
curl -X POST http://127.0.0.1:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "ChangeMe123!",
    "displayName": "Example User"
  }'
```

### Log in

```bash
curl -X POST http://127.0.0.1:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@example.com",
    "password": "replace-this-admin-password"
  }'
```

The response includes `accessToken`, `tokenType`, `expiresIn`, and the current
user.

## API Endpoints

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Public | Register a `USER` |
| `POST` | `/api/auth/login` | Public | Authenticate and receive a JWT |
| `GET` | `/api/auth/me` | Authenticated | Return the current user |
| `GET` | `/api/projects` | Authenticated | List projects with pagination |
| `GET` | `/api/projects/{id}` | Authenticated | Get one project |
| `POST` | `/api/projects` | `ADMIN` | Create a project |
| `PATCH` | `/api/projects/{id}` | `ADMIN` | Update a project |
| `DELETE` | `/api/projects/{id}` | `ADMIN` | Delete a project |
| `GET` | `/actuator/health` | Public | Application health check |

Project statuses are `PLANNED`, `ACTIVE`, `COMPLETED`, and `ARCHIVED`.

Pagination example:

```text
GET /api/projects?page=0&size=20
```

The maximum page size is `100`.

### Create a project as an administrator

```bash
curl -X POST http://127.0.0.1:8081/api/projects \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Company Website",
    "description": "Public website delivery",
    "status": "ACTIVE"
  }'
```

## Error Responses

Validation and application errors use a consistent JSON structure:

```json
{
  "timestamp": "2026-07-10T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/projects",
  "fields": {
    "name": "must not be blank"
  }
}
```

Common status codes:

- `400`: invalid input
- `401`: missing, invalid, or expired authentication
- `403`: authenticated user does not have permission
- `404`: resource does not exist
- `409`: email is already registered

## Database Migrations

Flyway migrations are stored in:

```text
src/main/resources/db/migration
```

`V1__initial_schema.sql` creates the `app_users` and `projects` tables. Flyway
runs automatically during startup. Hibernate uses `ddl-auto: validate`, so
schema changes must be added as new Flyway migrations instead of modifying the
database manually.

Never edit an applied migration in a shared environment. Add a new migration,
for example `V2__add_project_deadline.sql`.

## Tests

Start PostgreSQL and configure `DB_PASSWORD` and `JWT_SECRET` before running the
test suite.

Windows:

```powershell
.\mvnw.cmd test
```

Linux or macOS:

```bash
./mvnw test
```

The integration test verifies registration, login, JWT authorization, project
CRUD, and the `USER`/`ADMIN` permission boundary.

## Build a Production JAR

```bash
./mvnw clean package
```

Windows:

```powershell
.\mvnw.cmd clean package
```

The executable JAR is generated in `target/`:

```text
target/project-os-backend-0.0.1-SNAPSHOT.jar
```

Run it with production environment variables:

```bash
java -jar target/project-os-backend-0.0.1-SNAPSHOT.jar
```

## Project Structure

```text
src/main/java/vn/uytinmang/projectos/
|-- auth/       Registration, login, and JWT creation
|-- common/     Shared API exceptions and error responses
|-- config/     Security, CORS, and admin bootstrap configuration
|-- project/    Project entity, repository, service, and controller
`-- user/       User entity and repository

src/main/resources/
|-- application.yml
`-- db/migration/
```

## Production Checklist

- Generate unique database and JWT secrets; never reuse local examples.
- Keep PostgreSQL and the Spring port private behind a firewall.
- Put Nginx or another reverse proxy in front of the application.
- Set `CORS_ALLOWED_ORIGINS` to the real frontend origin.
- Run the JAR as a restricted service user, not as `root`.
- Store secrets in a root-readable environment file or secret manager.
- Remove bootstrap admin credentials after first startup.
- Back up PostgreSQL before applying new migrations.
- Verify health, authentication, permission failures, and Project persistence
  after every deployment.

## Current Scope

This repository currently owns authentication and Project persistence only.
Tasks, teams, documents, budgets, and other ProjectOS modules still require
separate backend migrations and API contracts before they can use PostgreSQL.
