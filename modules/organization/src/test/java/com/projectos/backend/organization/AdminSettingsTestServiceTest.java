package com.projectos.backend.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AdminSettingsTestServiceTest {
    private final AdminSettingsTestService service = new AdminSettingsTestService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsNineRouterAsTheConfiguredAiProvider() throws Exception {
        var result = service.testAi(true, mapper.readTree("{\"provider\":\"NINEROUTER\"}"));

        assertEquals(true, result.get("success"));
    }
}
