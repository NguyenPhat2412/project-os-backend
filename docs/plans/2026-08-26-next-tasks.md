# Nhiệm vụ tiếp theo — 26/08/2026

## Ưu tiên cao

- Kiểm tra end-to-end luồng đăng nhập, refresh session và truy cập workspace trên phiên thường và ẩn danh.
- Kiểm thử thủ công hai bảng phân quyền: toolbar cố định, cuộn dọc/ngang riêng, header sticky và responsive ở desktop/mobile.
- Hoàn thiện contract backend cho việc tạo cuộc trò chuyện trực tiếp để luồng Tin nhắn có thể mở cuộc trò chuyện thật, không tạo dữ liệu giả ở frontend.
- Chạy kiểm thử contract cho quyền truy cập tổ chức, nhân sự, danh bạ thành viên và các trạng thái lỗi 401/403/404/503.

## Ưu tiên trung bình

- Xác nhận toàn bộ Flyway migration trên database local sạch và database đang dùng; không xóa volume dữ liệu hiện tại.
- Rà soát các service còn gọi endpoint cũ và cập nhật về monolith API hiện tại.
- Bổ sung smoke test cho các page không có dữ liệu: nhân sự, hợp đồng, chấm công, phòng ban, đội nhóm và tin nhắn.

## Tiêu chí hoàn thành

- Backend health trả HTTP 200 và tất cả migration ở trạng thái hợp lệ.
- FE build, typecheck, lint, TanStack check và architecture check đều pass.
- Không có mock data nghiệp vụ hoặc fallback che giấu lỗi backend.
- Có bằng chứng kiểm thử responsive và các thao tác chính trên môi trường local.
