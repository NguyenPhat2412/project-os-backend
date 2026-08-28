# Swagger/OpenAPI của Project OS

`monolith-app` là nguồn OpenAPI duy nhất cho toàn bộ backend modular monolith.
Springdoc tự động lấy các controller của các module domain trên cùng classpath;
không chạy tài liệu riêng cho từng service.

## Địa chỉ local

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Tài liệu chỉ bao gồm các route `/api/v1/**` công khai cho frontend và loại trừ
`/api/v1/internal/**`. Actuator không được đưa vào tài liệu API nghiệp vụ.

## Xác thực khi dùng Try it out

Backend dùng cookie phiên:

- `PROJECT_OS_ACCESS`: access token HttpOnly.
- `XSRF-TOKEN` và header `X-XSRF-TOKEN`: bảo vệ các request mutation.

Đăng nhập qua API trước, sau đó dùng Swagger UI trên cùng origin. Với
`POST`, `PUT`, `PATCH` hoặc `DELETE`, gửi thêm giá trị CSRF tương ứng trong
header `X-XSRF-TOKEN`.

## Quy tắc môi trường

- Local/staging: bật bằng `OPENAPI_ENABLED=true`.
- Public documentation local/staging: chỉ bật có chủ đích bằng
  `OPENAPI_PUBLIC=true`.
- Production: `application-production.yml` tắt cả OpenAPI JSON và Swagger UI;
  không mở tài liệu API ra Internet.

Mọi thay đổi endpoint phải được kiểm tra trong
`MonolithStartupTest.swaggerDocumentsPublicMonolithApiWithoutInternalRoutes`.
