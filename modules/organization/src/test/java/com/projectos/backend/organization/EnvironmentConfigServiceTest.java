package com.projectos.backend.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.projectos.backend.platform.api.ApiException;

class EnvironmentConfigServiceTest {
    @Test
    void snapshotMasksSecretValuesAndExposesOnlyCanonicalKeys() {
        var service = new EnvironmentConfigService(Map.of(
                "DB_URL", "jdbc:postgresql://localhost:5432/project_os",
                "JWT_SECRET", "super-secret",
                "OBJECT_STORAGE_ACCESS_KEY", "sensitive-access-key",
                "GATEWAY_PORT", "18080",
                "S3_ACCESS_KEY", "legacy-access-key",
                "NOT_ALLOWED", "must-not-leak"));

        var snapshot = service.snapshot();

        assertEquals("jdbc:postgresql://localhost:5432/project_os", snapshot.get("DB_URL"));
        assertEquals(EnvironmentConfigService.MASKED_VALUE, snapshot.get("JWT_SECRET"));
        assertEquals(EnvironmentConfigService.MASKED_VALUE, snapshot.get("OBJECT_STORAGE_ACCESS_KEY"));
        org.junit.jupiter.api.Assertions.assertFalse(snapshot.containsKey("GATEWAY_PORT"));
        org.junit.jupiter.api.Assertions.assertFalse(snapshot.containsKey("S3_ACCESS_KEY"));
        org.junit.jupiter.api.Assertions.assertFalse(snapshot.containsKey("NOT_ALLOWED"));
    }

    @Test
    void updateRejectsUnknownKeysAndWritesAllowlistedValuesAtomically() throws Exception {
        Path file = Files.createTempFile("project-os-env", ".env");
        try {
            Files.writeString(file, "POSTGRES_PASSWORD=keep-this\nUNMANAGED_KEY=keep-this-too\n");
            var service = new EnvironmentConfigService(file, Map.of("CORS_ALLOWED_ORIGINS", "old"));

            assertThrows(ApiException.class,
                    () -> service.update(Map.of("GATEWAY_PORT", "18080")));
            service.update(Map.of("CORS_ALLOWED_ORIGINS", "https://new.example"));

            String content = Files.readString(file);
            org.junit.jupiter.api.Assertions.assertTrue(content.contains("POSTGRES_PASSWORD=keep-this"));
            org.junit.jupiter.api.Assertions.assertTrue(content.contains("UNMANAGED_KEY=keep-this-too"));
            org.junit.jupiter.api.Assertions.assertTrue(content.contains("CORS_ALLOWED_ORIGINS=https://new.example"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void snapshotReadsCurrentValuesFromConfiguredFile() throws Exception {
        Path file = Files.createTempFile("project-os-env", ".env");
        try {
            Files.writeString(file, "DB_URL=jdbc:postgresql://current.example:5432/project_os\nJWT_SECRET=secret\n");
            var service = new EnvironmentConfigService(file, Map.of("DB_URL", "jdbc:postgresql://stale.example:5432/project_os"));

            assertEquals("jdbc:postgresql://current.example:5432/project_os", service.snapshot().get("DB_URL"));
            assertEquals(EnvironmentConfigService.MASKED_VALUE, service.snapshot().get("JWT_SECRET"));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
