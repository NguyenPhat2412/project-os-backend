package com.projectos.backend.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentConfigServiceTest {
    @Test
    void snapshotMasksSecretValuesAndExposesOnlyAllowlistedKeys() {
        var service = new EnvironmentConfigService(Map.of(
                "PROJECT_OS_API_PUBLIC_URL", "http://localhost:8081",
                "JWT_SECRET", "super-secret",
                "NOT_ALLOWED", "must-not-leak"));

        var snapshot = service.snapshot();

        assertEquals("http://localhost:8081", snapshot.get("PROJECT_OS_API_PUBLIC_URL"));
        assertEquals(EnvironmentConfigService.MASKED_VALUE, snapshot.get("JWT_SECRET"));
        org.junit.jupiter.api.Assertions.assertFalse(snapshot.containsKey("NOT_ALLOWED"));
    }

    @Test
    void updateRejectsUnknownKeysAndWritesAllowlistedValuesAtomically() throws Exception {
        Path file = Files.createTempFile("project-os-env", ".env");
        try {
            Files.writeString(file, "POSTGRES_PASSWORD=keep-this\nUNMANAGED_KEY=keep-this-too\n");
            var service = new EnvironmentConfigService(file, Map.of("PROJECT_OS_API_PUBLIC_URL", "old"));

            assertThrows(IllegalArgumentException.class,
                    () -> service.update(Map.of("NOT_ALLOWED", "value")));
            service.update(Map.of("PROJECT_OS_API_PUBLIC_URL", "new"));

            String content = Files.readString(file);
            org.junit.jupiter.api.Assertions.assertTrue(content.contains("POSTGRES_PASSWORD=keep-this"));
            org.junit.jupiter.api.Assertions.assertTrue(content.contains("UNMANAGED_KEY=keep-this-too"));
            org.junit.jupiter.api.Assertions.assertTrue(content.contains("PROJECT_OS_API_PUBLIC_URL=new"));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
