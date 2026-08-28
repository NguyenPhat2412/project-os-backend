package com.projectos.backend.operations.email;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.projectos.backend.platform.api.ApiException;

@Component
public class EmailTemplateSanitizer {
    private static final Pattern BLOCKS = Pattern.compile("(?is)<(script|style|iframe|object|embed)[^>]*>.*?</\\1>");
    private static final Pattern EVENTS = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JAVASCRIPT = Pattern.compile("(?i)javascript:");

    public String sanitize(String html) {
        if (html == null || html.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "email_body_required", "Nội dung email là bắt buộc.");
        return JAVASCRIPT.matcher(EVENTS.matcher(BLOCKS.matcher(html).replaceAll("")).replaceAll("")).replaceAll("");
    }

    public void validateVariables(String html, List<String> allowed) {
        var matcher = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*}}\\s*").matcher(html);
        while (matcher.find() && (allowed == null || !allowed.contains(matcher.group(1)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email_template_variable_invalid", "Mẫu email chứa biến chưa được cho phép.");
        }
    }
}
