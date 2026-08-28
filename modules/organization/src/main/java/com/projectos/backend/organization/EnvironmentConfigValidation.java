package com.projectos.backend.organization;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import com.projectos.backend.platform.api.ApiException;

/** Validates the small, explicit environment surface exposed to root admins. */
public final class EnvironmentConfigValidation {
    private static final Set<String> URL_KEYS = EnvironmentConfigCatalog.URL_KEYS;
    private static final Set<String> JDBC_URL_KEYS = EnvironmentConfigCatalog.JDBC_URL_KEYS;
    private static final Set<String> PORT_KEYS = EnvironmentConfigCatalog.PORT_KEYS;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private EnvironmentConfigValidation() { }

    public static Map<String, String> validate(Map<String, String> input) {
        if (input == null) {
            throw invalid("environment_payload_invalid", "Cấu hình môi trường không hợp lệ.");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key == null || !EnvironmentConfigService.isAllowedKey(key)) {
                throw invalid("environment_key_not_allowed", "Cấu hình này không được phép cập nhật.");
            }
            String trimmed = value == null ? "" : value.trim();
            if (URL_KEYS.contains(key) && !trimmed.isEmpty()) validateUrl(trimmed);
            if (JDBC_URL_KEYS.contains(key) && !trimmed.isEmpty()) validateJdbcUrl(trimmed);
            if (PORT_KEYS.contains(key) && !trimmed.isEmpty()) validatePort(trimmed);
            if ("BOOTSTRAP_ADMIN_EMAIL".equals(key) && !trimmed.isEmpty() && !EMAIL.matcher(trimmed).matches()) {
                throw invalid("invalid_environment_email", "Email quản trị không đúng định dạng.");
            }
            normalized.put(key, trimmed);
        });
        return normalized;
    }

    private static void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null || value.matches(".*\\s+.*")) {
                throw invalid("invalid_environment_url", "Địa chỉ kết nối không đúng định dạng.");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid_environment_url", "Địa chỉ kết nối không đúng định dạng.");
        }
    }

    private static void validatePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw invalid("invalid_environment_port", "Cổng kết nối phải nằm trong khoảng 1 đến 65535.");
        }
    }

    private static void validateJdbcUrl(String value) {
        if (!value.startsWith("jdbc:postgresql://") || value.matches(".*\\s+.*")
                || value.length() <= "jdbc:postgresql://".length()
                || value.matches("(?i).*([?&](password|pass|sslkey)=|//[^/]*:[^/@]*@).*")) {
            throw invalid("invalid_environment_url", "Địa chỉ kết nối không đúng định dạng.");
        }
    }

    private static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
