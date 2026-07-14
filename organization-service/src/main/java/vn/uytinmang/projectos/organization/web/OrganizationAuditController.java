package vn.uytinmang.projectos.organization.web;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.organization.domain.OrganizationAuditService;
import vn.uytinmang.projectos.platform.api.PageResponse;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/audit")
class OrganizationAuditController {
    private final OrganizationAuditService service;
    OrganizationAuditController(OrganizationAuditService service) { this.service = service; }

    @GetMapping
    PageResponse<OrganizationAuditService.AuditView> list(@PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return service.list(organizationId, page, size, UUID.fromString(jwt.getClaimAsString("uid")),
                "ROOT_ADMIN".equals(jwt.getClaimAsString("role")));
    }
}
