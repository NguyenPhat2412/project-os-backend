package com.projectos.backend.organization.web;

import com.projectos.backend.organization.AdminSettingsTestService;
import com.projectos.backend.platform.api.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/admin/settings/tests")
public class AdminSettingsTestController {
    private final AdminSettingsTestService service;

    public AdminSettingsTestController(AdminSettingsTestService service) {
        this.service = service;
    }

    @PostMapping("/email")
    ApiResponse<Map<String, Object>> email(@RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.testEmail(root(jwt), body));
    }

    @PostMapping("/telegram")
    ApiResponse<Map<String, Object>> telegram(@RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.testTelegram(root(jwt), body));
    }

    @PostMapping("/ai")
    ApiResponse<Map<String, Object>> ai(@RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.testAi(root(jwt), body));
    }

    @PostMapping("/backup")
    ApiResponse<Map<String, Object>> backup(@RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.testBackup(root(jwt), body));
    }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }
}
