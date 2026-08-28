package com.projectos.backend.organization;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import com.projectos.backend.platform.api.ApiException;

/** Validates the organization-owned AI policy before it is persisted. */
public final class AiSettingsValidation {
    private AiSettingsValidation() {}

    public static void validate(JsonNode ai) {
        if (ai == null || ai.isNull()) return;
        if (!ai.isObject()) throw invalid("ai_settings_invalid", "Cấu hình Trợ lý không hợp lệ.");

        JsonNode modelName = ai.get("modelName");
        String defaultModelId = "";
        if (modelName != null && !modelName.isNull()
                && (!modelName.isTextual() || modelName.asText().trim().length() > 200)) {
            throw invalid("ai_model_name_invalid", "Tên mô hình không hợp lệ.");
        }
        if (modelName != null && modelName.isTextual()) defaultModelId = modelName.asText().trim();

        JsonNode allowed = ai.get("allowedModelIds");
        if (allowed != null && !allowed.isNull()) {
            if (!allowed.isArray() || allowed.size() > 100) {
                throw invalid("ai_allowed_models_invalid", "Danh sách mô hình được phép không hợp lệ.");
            }
            Set<String> unique = new HashSet<>();
            for (JsonNode model : allowed) {
                if (!model.isTextual() || model.asText().isBlank() || model.asText().trim().length() > 200
                        || !unique.add(model.asText().trim())) {
                    throw invalid("ai_allowed_models_invalid", "Danh sách mô hình được phép không hợp lệ.");
                }
            }
            if (!defaultModelId.isBlank() && !unique.contains(defaultModelId)) {
                throw invalid("ai_default_model_not_allowed", "Mô hình mặc định phải nằm trong danh sách mô hình được phép.");
            }
        }

        JsonNode temperature = ai.get("temperature");
        if (temperature != null && !temperature.isNull()
                && (!temperature.isNumber() || temperature.doubleValue() < 0 || temperature.doubleValue() > 2)) {
            throw invalid("ai_temperature_invalid", "Độ linh hoạt câu trả lời phải nằm trong khoảng cho phép.");
        }

        JsonNode maxTokens = ai.get("maxOutputTokens");
        if (maxTokens != null && !maxTokens.isNull()
                && (!maxTokens.isIntegralNumber() || maxTokens.longValue() < 1 || maxTokens.longValue() > 100_000)) {
            throw invalid("ai_max_tokens_invalid", "Số token phản hồi không hợp lệ.");
        }
    }

    private static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
