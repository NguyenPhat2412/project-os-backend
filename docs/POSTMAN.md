# Test ProjectOS bằng Postman

## 1. Chạy PostgreSQL

Tại thư mục `project-os-backend`:

```powershell
docker compose up -d
```

## 2. Cấu hình IntelliJ

Mở **Run > Edit Configurations > ProjectOsBackendApplication** và thêm vào **Environment variables**:

```text
DB_PASSWORD=local_project_os_password;JWT_SECRET=local-development-jwt-secret-32-characters-minimum;BOOTSTRAP_ADMIN_EMAIL=admin@projectos.local;BOOTSTRAP_ADMIN_PASSWORD=Admin123!;BOOTSTRAP_ADMIN_NAME=Local Admin
```

Chạy application và đợi dòng `Tomcat started on port 8081`.

## 3. Import và chạy Postman

1. Chọn **Import** trong Postman.
2. Chọn file `postman/ProjectOS.postman_collection.json`.
3. Mở collection **ProjectOS Backend > Variables**.
4. Điền `adminPassword` là `Admin123!` rồi Save.
5. Gửi các request theo thứ tự từ 1 đến 9.

Collection tự tạo email user mới, tự lưu JWT vào `adminToken`/`userToken`, và tự lưu `projectId`.

## API chính

| Method | URL | Quyền |
| --- | --- | --- |
| POST | `/api/auth/register` | Public, luôn tạo USER |
| POST | `/api/auth/login` | Public |
| GET | `/api/auth/me` | Đã đăng nhập |
| GET | `/api/projects` | Đã đăng nhập |
| GET | `/api/projects/{id}` | Đã đăng nhập |
| POST | `/api/projects` | ADMIN |
| PATCH | `/api/projects/{id}` | ADMIN |
| DELETE | `/api/projects/{id}` | ADMIN |
