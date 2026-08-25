# QUY TẮC KIẾN TRÚC BẮT BUỘC - DỰ ÁN PROJECT-OS

## 1. MÔ HÌNH KIẾN TRÚC 3 TẦNG CHUẨN (3-TIER ARCHITECTURE)
Hệ thống được chia thành 3 phần rõ ràng và độc lập:

1. **DATABASE (PostgreSQL)**:
   - Là nguồn chân lý duy nhất (Single Source of Truth) của toàn bộ hệ thống.
   - Toàn bộ thực thể doanh nghiệp (nhân viên, phòng ban, chấm công, ca làm việc, hợp đồng, hồ sơ công ty, nghỉ phép, KPI, hoạt động, thông báo, v.v.) được lưu trữ và truy vấn trực tiếp từ các bảng vật lý trong PostgreSQL `project_os`.

2. **BACKEND (BE)**:
   - Phụ trách toàn bộ logic điều khiển, nghiệp vụ, kết nối và xử lý dữ liệu với Database.
   - Cung cấp các RESTful API endpoints chuẩn xác.

3. **FRONTEND (FE)**:
   - Phụ trách giao diện hiển thị, tương tác người dùng và gọi API từ BE/DB.
   - Nhận dữ liệu động thông qua React Query hooks (`useEmployeesQuery`, `useDepartmentsQuery`, `useAttendanceQuery`, `useContractsQuery`, v.v.) hoặc `fetch()`.

---

## 2. NGUYÊN TẮC BẤT DI BẤT DỊCH (STRICT RULES)
1. **TUYỆT ĐỐI KHÔNG HARDCODE MOCK DATA**:
   - Nghiêm cấm tạo các mảng mock tĩnh bên trong các component FE (ví dụ: `const [employeesList, setEmployeesList] = useState([...])` chứa data tĩnh).
   - Nghiêm cấm viết mock/seed data giả định trong các file code logic ở FE và BE.
2. **LUÔN LẤY DỮ LIỆU ĐỘNG TỪ DATABASE**:
   - Tất cả dropdown, danh sách, bảng biểu, thống kê, chi tiết nhân sự, phòng ban, ca kíp... PHẢI gọi API để lấy dữ liệu thực từ PostgreSQL.
   - Mọi thao tác Thêm / Sửa / Xóa / Chấm công / Phê duyệt PHẢI ghi trực tiếp vào Database PostgreSQL qua API.
3. **ĐỒNG BỘ DỮ LIỆU ĐÚNG TÀI KHOẢN ĐĂNG NHẬP**:
   - Thông tin hiển thị phải lấy từ phiên làm việc thực tế (`useAuth`, `useWorkspace`, `/api/v1/auth/me`), không dùng tên tĩnh hay gán cứng.
4. **MÔI TRƯỜNG LÀM VIỆC**:
   - Phát triển và kiểm thử trực tiếp trên `http://localhost:3000`.
   - Không push remote hay deploy VPS khi chưa có yêu cầu trực tiếp từ User.
