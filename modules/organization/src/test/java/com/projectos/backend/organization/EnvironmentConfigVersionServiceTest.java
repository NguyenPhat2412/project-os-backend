package com.projectos.backend.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.projectos.backend.platform.api.ApiException;

class EnvironmentConfigVersionServiceTest {
    @Test
    void productionRejectsApplyBeforeReadingOrWritingConfiguration() throws Exception {
        var service = new EnvironmentConfigVersionService(
                new EnvironmentConfigService(Map.of()),
                null,
                Files.createTempDirectory("project-os-snapshots").toString(),
                true);

        ApiException error = assertThrows(ApiException.class, () ->
                service.apply(true, UUID.randomUUID(), Map.of("CORS_ALLOWED_ORIGINS", "https://example.test")));

        assertEquals("environment_config_read_only", error.code());
    }

    @Test
    void productionRejectsRollbackBeforeLookingUpVersion() throws Exception {
        var service = new EnvironmentConfigVersionService(
                new EnvironmentConfigService(Map.of()),
                null,
                Files.createTempDirectory("project-os-snapshots").toString(),
                true);

        ApiException error = assertThrows(ApiException.class, () ->
                service.rollback(true, UUID.randomUUID(), UUID.randomUUID()));

        assertEquals("environment_config_read_only", error.code());
    }
}
