package vn.uytinmang.projectos.organization.web;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.organization.domain.OrganizationApplicationService;
import vn.uytinmang.projectos.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/me")
class WorkspaceController {
    private final OrganizationApplicationService service;

    WorkspaceController(OrganizationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/workspace")
    ApiResponse<OrganizationApplicationService.Workspace> workspace(
            @RequestParam(required = false) UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.workspace(organizationId, UUID.fromString(jwt.getClaimAsString("uid")),
                "ROOT_ADMIN".equals(jwt.getClaimAsString("role"))));
    }
}
