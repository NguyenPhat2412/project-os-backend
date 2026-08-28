package com.projectos.backend.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.projectos.backend.platform.api.ApiException;

class AiSettingsValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsNonArrayModelAllowList() throws Exception {
        ApiException error = assertThrows(ApiException.class, () ->
                AiSettingsValidation.validate(mapper.readTree("{\"allowedModelIds\":\"model-a\"}")));

        assertEquals("ai_allowed_models_invalid", error.code());
    }

    @Test
    void rejectsOutOfRangeTemperature() throws Exception {
        ApiException error = assertThrows(ApiException.class, () ->
                AiSettingsValidation.validate(mapper.readTree("{\"temperature\":2.1}")));

        assertEquals("ai_temperature_invalid", error.code());
    }

    @Test
    void acceptsValidModelSettings() throws Exception {
        AiSettingsValidation.validate(mapper.readTree(
                "{\"modelName\":\"model-a\",\"allowedModelIds\":[\"model-a\",\"model-b\"],\"temperature\":0.4,\"maxOutputTokens\":2000}"));
    }

    @Test
    void rejectsDefaultModelOutsideAllowList() throws Exception {
        ApiException error = assertThrows(ApiException.class, () ->
                AiSettingsValidation.validate(mapper.readTree(
                        "{\"modelName\":\"model-c\",\"allowedModelIds\":[\"model-a\",\"model-b\"]}")));

        assertEquals("ai_default_model_not_allowed", error.code());
    }
}
