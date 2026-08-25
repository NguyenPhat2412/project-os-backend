package com.projectos.backend.platform.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Backend-owned catalog for stable API error names and safe user-facing messages.
 * Unknown business codes intentionally fall back to a status-level message so
 * implementation details are never exposed to the browser.
 */
@Component
public class ApiErrorCatalog {
    private static final Map<String, Entry> ENTRIES = Map.ofEntries(
            Map.entry("unauthorized", new Entry("Phiên đăng nhập không hợp lệ", "Vui lòng đăng nhập lại.")),
            Map.entry("invalid_credentials", new Entry("Thông tin đăng nhập không đúng", "Email hoặc mật khẩu không đúng.")),
            Map.entry("forbidden", new Entry("Không có quyền truy cập", "Bạn không có quyền thực hiện thao tác này.")),
            Map.entry("organization_access_denied", new Entry("Không có quyền truy cập tổ chức", "Bạn không có quyền truy cập dữ liệu này.")),
            Map.entry("organization_admin_required", new Entry("Cần quyền quản trị tổ chức", "Thao tác này yêu cầu quyền quản trị tổ chức.")),
            Map.entry("employee_not_found", new Entry("Không tìm thấy nhân sự", "Nhân sự này không còn tồn tại hoặc đã bị xóa.")),
            Map.entry("user_not_found", new Entry("Không tìm thấy tài khoản", "Tài khoản này không còn tồn tại hoặc đã bị xóa.")),
            Map.entry("project_not_found", new Entry("Không tìm thấy dự án", "Dự án này không còn tồn tại hoặc đã bị xóa.")),
            Map.entry("department_not_found", new Entry("Không tìm thấy phòng ban", "Phòng ban này không còn tồn tại hoặc đã bị xóa.")),
            Map.entry("invalid_body", new Entry("Dữ liệu gửi lên không hợp lệ", "Vui lòng kiểm tra lại dữ liệu đã nhập.")),
            Map.entry("validation_failed", new Entry("Dữ liệu chưa hợp lệ", "Vui lòng kiểm tra các trường được đánh dấu.")),
            Map.entry("invalid_date", new Entry("Ngày không hợp lệ", "Vui lòng nhập ngày theo đúng định dạng.")),
            Map.entry("invalid_password", new Entry("Mật khẩu không hợp lệ", "Vui lòng kiểm tra lại mật khẩu.")),
            Map.entry("email_exists", new Entry("Email đã tồn tại", "Email này đã được sử dụng.")),
            Map.entry("legacy_id_exists", new Entry("Dữ liệu đã tồn tại", "Mã dữ liệu này đã được sử dụng.")),
            Map.entry("password_unchanged", new Entry("Mật khẩu chưa thay đổi", "Mật khẩu mới phải khác mật khẩu hiện tại.")),
            Map.entry("resource_immutable", new Entry("Không thể thay đổi dữ liệu", "Dữ liệu này không hỗ trợ thao tác thay đổi.")),
            Map.entry("file_too_large", new Entry("Tệp quá lớn", "Tệp vượt quá dung lượng cho phép.")),
            Map.entry("empty_file", new Entry("Tệp rỗng", "Vui lòng chọn tệp có dữ liệu.")),
            Map.entry("storage_unavailable", new Entry("Kho lưu trữ tạm thời không khả dụng", "Vui lòng thử lại sau.")),
            Map.entry("organization_service_unavailable", new Entry("Dịch vụ tổ chức tạm thời gián đoạn", "Vui lòng thử lại sau.")),
            Map.entry("project_service_unavailable", new Entry("Dịch vụ dự án tạm thời gián đoạn", "Vui lòng thử lại sau.")),
            Map.entry("record_not_found", new Entry("Không tìm thấy dữ liệu", "Dữ liệu không còn tồn tại hoặc đã bị xóa.")),
            Map.entry("not_found", new Entry("Không tìm thấy dữ liệu", "Dữ liệu không còn tồn tại hoặc đã bị xóa.")),
            Map.entry("rate_limited", new Entry("Bạn thao tác quá nhanh", "Vui lòng chờ một lát rồi thử lại.")),
            Map.entry("internal_error", new Entry("Lỗi hệ thống", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.")),
            Map.entry("backend_unavailable", new Entry("Hệ thống tạm thời gián đoạn", "Không thể kết nối đến hệ thống. Vui lòng thử lại sau."))
    );

    public Descriptor describe(HttpStatus status, String code) {
        Entry entry = ENTRIES.get(code);
        if (entry != null) return new Descriptor(entry.name(), entry.message());
        if ((status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNPROCESSABLE_ENTITY)
                && code != null && (code.startsWith("invalid_") || code.startsWith("missing_") || code.endsWith("_required"))) {
            return new Descriptor("Dữ liệu không hợp lệ", "Vui lòng kiểm tra lại dữ liệu đã nhập.");
        }
        if (code != null && code.endsWith("_not_found")) {
            return new Descriptor("Không tìm thấy dữ liệu", "Dữ liệu không còn tồn tại hoặc đã bị xóa.");
        }
        if (code != null && (code.endsWith("_access_denied") || code.endsWith("_denied") || code.endsWith("_forbidden"))) {
            return new Descriptor("Không có quyền truy cập", "Bạn không có quyền thực hiện thao tác này.");
        }
        if (code != null && (code.endsWith("_exists") || code.endsWith("_already_linked") || code.startsWith("cannot_"))) {
            return new Descriptor("Dữ liệu bị xung đột", "Dữ liệu vừa được thay đổi hoặc đã tồn tại.");
        }
        if (code != null && code.endsWith("_unavailable")) {
            return new Descriptor("Dịch vụ tạm thời gián đoạn", "Vui lòng thử lại sau.");
        }
        return new Descriptor(defaultName(status), defaultMessage(status));
    }

    private String defaultName(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST, UNPROCESSABLE_ENTITY -> "Dữ liệu không hợp lệ";
            case UNAUTHORIZED -> "Phiên đăng nhập không hợp lệ";
            case FORBIDDEN -> "Không có quyền truy cập";
            case NOT_FOUND -> "Không tìm thấy dữ liệu";
            case METHOD_NOT_ALLOWED -> "Thao tác không được hỗ trợ";
            case CONFLICT -> "Dữ liệu bị xung đột";
            case PAYLOAD_TOO_LARGE -> "Dữ liệu vượt quá giới hạn";
            case TOO_MANY_REQUESTS -> "Bạn thao tác quá nhanh";
            case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> "Dịch vụ tạm thời gián đoạn";
            default -> "Lỗi hệ thống";
        };
    }

    private String defaultMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST, UNPROCESSABLE_ENTITY -> "Vui lòng kiểm tra lại dữ liệu đã nhập.";
            case UNAUTHORIZED -> "Vui lòng đăng nhập lại.";
            case FORBIDDEN -> "Bạn không có quyền thực hiện thao tác này.";
            case NOT_FOUND -> "Dữ liệu không còn tồn tại hoặc đã bị xóa.";
            case METHOD_NOT_ALLOWED -> "Thao tác này không được hỗ trợ.";
            case CONFLICT -> "Dữ liệu vừa được thay đổi, vui lòng tải lại và thử lại.";
            case PAYLOAD_TOO_LARGE -> "Dữ liệu vượt quá giới hạn cho phép.";
            case TOO_MANY_REQUESTS -> "Vui lòng chờ một lát rồi thử lại.";
            case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> "Hệ thống tạm thời gián đoạn. Vui lòng thử lại sau.";
            default -> "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.";
        };
    }

    public record Descriptor(String name, String message) {}

    private record Entry(String name, String message) {}
}
