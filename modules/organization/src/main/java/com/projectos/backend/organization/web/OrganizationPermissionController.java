package com.projectos.backend.organization.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.projectos.backend.organization.domain.OrganizationPermissionService;
import com.projectos.backend.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/permissions")
public class OrganizationPermissionController {
    private final OrganizationPermissionService service;
    OrganizationPermissionController(OrganizationPermissionService service) { this.service = service; }

    @GetMapping
    ApiResponse<Map<String, List<String>>> list(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.list(organizationId, actor(jwt), root(jwt)));
    }

    @PutMapping
    ApiResponse<Map<String, List<String>>> replace(@PathVariable UUID organizationId,
            @RequestBody Map<String, List<String>> permissions, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.replace(organizationId, permissions, actor(jwt), root(jwt)));
    }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }
}
