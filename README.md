# ProjectOS Backend

Spring Boot 4, Java 21 and PostgreSQL backend for ProjectOS.

## Run locally

```powershell
docker compose up -d
$env:DB_PASSWORD='local_project_os_password'
$env:JWT_SECRET='local-development-jwt-secret-32-characters-minimum'
.\mvnw.cmd spring-boot:run
```

The API runs at `http://127.0.0.1:8081`.
