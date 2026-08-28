package com.projectos.backend.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.projectos.backend.platform.api.ApiException;

class EnvironmentConfigValidationTest {
    @Test
    void rejectsInvalidUrlWithStableErrorCode() {
        ApiException error = assertThrows(ApiException.class, () ->
                EnvironmentConfigValidation.validate(Map.of("NINEROUTER_URL", "not-a-url")));

        assertEquals("invalid_environment_url", error.code());
    }

    @Test
    void rejectsCredentialsEmbeddedInConnectionUrls() {
        ApiException error = assertThrows(ApiException.class, () ->
                EnvironmentConfigValidation.validate(Map.of(
                        "NINEROUTER_URL", "https://user:password@example.test/api")));

        assertEquals("invalid_environment_url", error.code());
    }

    @Test
    void rejectsPasswordsEmbeddedInJdbcQueryParameters() {
        ApiException error = assertThrows(ApiException.class, () ->
                EnvironmentConfigValidation.validate(Map.of(
                        "DB_URL", "jdbc:postgresql://localhost:5432/project_os?password=secret")));

        assertEquals("invalid_environment_url", error.code());
    }

    @Test
    void rejectsPortOutsideTcpRange() {
        ApiException error = assertThrows(ApiException.class, () ->
                EnvironmentConfigValidation.validate(Map.of("REDIS_PORT", "70000")));

        assertEquals("invalid_environment_port", error.code());
    }

    @Test
    void rejectsInvalidEmail() {
        ApiException error = assertThrows(ApiException.class, () ->
                EnvironmentConfigValidation.validate(Map.of("BOOTSTRAP_ADMIN_EMAIL", "invalid-email")));

        assertEquals("invalid_environment_email", error.code());
    }

    @Test
    void rejectsUnknownKey() {
        ApiException error = assertThrows(ApiException.class, () ->
                EnvironmentConfigValidation.validate(Map.of("GATEWAY_PORT", "18080")));

        assertEquals("environment_key_not_allowed", error.code());
    }

    @Test
    void acceptsValidValuesAndTrimsThem() {
        Map<String, String> result = EnvironmentConfigValidation.validate(Map.of(
                "NINEROUTER_URL", " https://example.test ",
                "REDIS_PORT", "6379",
                "DB_URL", " jdbc:postgresql://localhost:5432/project_os "));

        assertEquals("https://example.test", result.get("NINEROUTER_URL"));
        assertEquals("6379", result.get("REDIS_PORT"));
        assertEquals("jdbc:postgresql://localhost:5432/project_os", result.get("DB_URL"));
    }
}
