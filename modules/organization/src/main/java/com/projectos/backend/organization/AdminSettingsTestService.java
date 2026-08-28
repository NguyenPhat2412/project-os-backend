package com.projectos.backend.organization;

import com.projectos.backend.platform.api.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import tools.jackson.databind.JsonNode;

@Service
public class AdminSettingsTestService {
    @Value("${SMTP_USERNAME:}")
    private String smtpUsername = "";

    @Value("${SMTP_PASSWORD:}")
    private String smtpPassword = "";

    public Map<String, Object> testEmail(boolean root, JsonNode body) {
        requireRoot(root);
        String host = text(body, "smtpHost");
        int port = number(body, "smtpPort", 587);
        String sender = text(body, "smtpSenderAddress");
        String recipient = text(body, "recipientEmail");
        String encryption = text(body, "smtpEncryption");
        if (host.isBlank() || port < 1 || port > 65535
                || !sender.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || !recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || smtpUsername.isBlank() || smtpPassword.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email_configuration_incomplete",
                    "Vui lòng bổ sung máy chủ, tài khoản bảo mật và địa chỉ email kiểm tra hợp lệ.");
        }
        try {
            JavaMailSenderImpl mail = new JavaMailSenderImpl();
            mail.setHost(host);
            mail.setPort(port);
            mail.setUsername(smtpUsername);
            mail.setPassword(smtpPassword);
            var properties = mail.getJavaMailProperties();
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "TLS".equalsIgnoreCase(encryption) || "STARTTLS".equalsIgnoreCase(encryption));
            properties.put("mail.smtp.ssl.enable", "SSL".equalsIgnoreCase(encryption));
            properties.put("mail.smtp.connectiontimeout", "10000");
            properties.put("mail.smtp.timeout", "15000");
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(sender);
            helper.setTo(recipient);
            helper.setSubject("Kiểm tra cấu hình email doanh nghiệp");
            helper.setText("Đây là email kiểm tra cấu hình gửi thư của hệ thống.");
            mail.send(message);
            return result("Đã gửi email kiểm tra. Vui lòng kiểm tra hộp thư nhận.");
        } catch (Exception ignored) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "email_smtp_test_failed",
                    "Không thể kết nối máy chủ email. Vui lòng kiểm tra cấu hình và thử lại.");
        }
    }

    public Map<String, Object> testTelegram(boolean root, JsonNode body) {
        requireRoot(root);
        if (text(body, "telegramChatId").isBlank() || isMasked(text(body, "telegramBotToken"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "telegram_configuration_incomplete",
                    "Vui lòng bổ sung mã truy cập và nhóm nhận thông báo.");
        }
        return result("Cấu hình Telegram đã sẵn sàng để kiểm tra.");
    }

    public Map<String, Object> testAi(boolean root, JsonNode body) {
        requireRoot(root);
        String provider = text(body, "provider");
        if (!AI_PROVIDERS.contains(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ai_configuration_incomplete",
                    "Vui lòng chọn nhà cung cấp Trợ lý phù hợp.");
        }
        return result("Cấu hình Trợ lý đã sẵn sàng để kiểm tra.");
    }

    public Map<String, Object> testBackup(boolean root, JsonNode body) {
        requireRoot(root);
        int retention = number(body, "backupRetentionDays", 0);
        if (retention < 1 || retention > 3650) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "backup_configuration_incomplete",
                    "Thời gian lưu bản sao phải nằm trong khoảng 1 đến 3650 ngày.");
        }
        return result("Cấu hình sao lưu đã sẵn sàng để kiểm tra.");
    }

    private static final java.util.Set<String> AI_PROVIDERS = java.util.Set.of("NINEROUTER");

    private static boolean isMasked(String value) {
        return value.isBlank() || EnvironmentConfigService.MASKED_VALUE.equals(value);
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static int number(JsonNode body, String field, int fallback) {
        JsonNode value = body == null ? null : body.get(field);
        return value == null || !value.isNumber() ? fallback : value.asInt(fallback);
    }

    private static Map<String, Object> result(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        return result;
    }

    private static void requireRoot(boolean root) {
        if (!root) throw new ApiException(HttpStatus.FORBIDDEN, "root_admin_required", "Chỉ quản trị cấp cao mới được quản lý cấu hình này.");
    }
}
