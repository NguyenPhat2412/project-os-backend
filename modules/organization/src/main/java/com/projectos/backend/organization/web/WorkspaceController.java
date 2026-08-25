package com.projectos.backend.organization.web;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.projectos.backend.organization.domain.OrganizationApplicationService;
import com.projectos.backend.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/me")
class WorkspaceController {
    private final OrganizationApplicationService service;

    WorkspaceController(OrganizationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/workspace")
    ApiResponse<OrganizationApplicationService.Workspace> workspace(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String organizationSlug,
            @RequestHeader(value = "x-organization-id", required = false) UUID organizationHeader,
            @AuthenticationPrincipal Jwt jwt) {
        UUID selectedOrganization = organizationId != null ? organizationId : organizationHeader;
        return ApiResponse.of(service.workspace(selectedOrganization, organizationSlug, UUID.fromString(jwt.getClaimAsString("uid")),
                "ROOT_ADMIN".equals(jwt.getClaimAsString("role"))));
    }
}
