---
name: strict-3tier-no-mock-data
description: Enforces strict 3-tier architecture with zero mock or hardcoded data. Database is PostgreSQL, Backend handles logic, Frontend strictly displays dynamic data fetched from APIs.
---

# Quy Tắc Kiến Trúc 3 Tầng Tuyệt Đối Không Hardcode Mock Data

## 1. Phân định rõ 3 tầng:
1. **Database**: Toàn bộ dữ liệu doanh nghiệp được lưu trữ trong PostgreSQL (`project_os`).
2. **Backend**: Cung cấp API, xử lý nghiệp vụ và truy vấn CSDL.
3. **Frontend**: Hiển thị dữ liệu nhận được từ Backend API / React Query hooks.

## 2. Quy tắc bắt buộc:
- **KHÔNG BAO GIỜ hardcode danh sách hoặc mock arrays** trong FE components (ví dụ: `useState([{...}])` với dữ liệu tĩnh).
- **MỌI dropdown, table, card, filter, profile** phải fetch dữ liệu thực từ API (`useEmployeesQuery`, `useDepartmentsQuery`, `useAttendanceQuery`, `useContractsQuery`, v.v.).
- **Tài khoản người dùng** phải lấy từ auth session (`useAuth`, `useWorkspace`), không dùng tên tĩnh.
