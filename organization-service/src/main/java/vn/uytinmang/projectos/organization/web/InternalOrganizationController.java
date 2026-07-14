package vn.uytinmang.projectos.organization.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.organization.domain.OrganizationApplicationService;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.ApiResponse;
import vn.uytinmang.projectos.platform.api.PageResponse;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/internal/organizations")
class InternalOrganizationController {
    private final OrganizationApplicationService service;
    private final byte[] token;
    InternalOrganizationController(OrganizationApplicationService service, @Value("${app.internal-token}") String token) { this.service = service; this.token = token.getBytes(StandardCharsets.UTF_8); }
    @GetMapping("/{organizationId}/employees/by-user/{userId}")
    ApiResponse<OrganizationController.EmployeeView> employeeByUser(@PathVariable UUID organizationId, @PathVariable UUID userId, @RequestHeader(value = "X-Internal-Token", required = false) String presented) {
        requireToken(presented);
        return ApiResponse.of(service.employeeByUser(organizationId, userId));
    }
    @GetMapping("/{organizationId}/employees/{employeeId}")
    ApiResponse<OrganizationController.EmployeeView> employee(@PathVariable UUID organizationId, @PathVariable UUID employeeId, @RequestHeader(value = "X-Internal-Token", required = false) String presented) {
        requireToken(presented);
        return ApiResponse.of(service.employee(organizationId, employeeId));
    }
    @GetMapping("/{organizationId}/employees/by-supervisor/{supervisorId}")
    PageResponse<OrganizationController.EmployeeView> directReports(@PathVariable UUID organizationId,
            @PathVariable UUID supervisorId, @RequestHeader(value = "X-Internal-Token", required = false) String presented) {
        requireToken(presented);
        return service.directReports(organizationId, supervisorId, 0, 200);
    }
    @GetMapping("/{organizationId}/access/{userId}")
    ApiResponse<OrganizationApplicationService.InternalAccess> access(@PathVariable UUID organizationId, @PathVariable UUID userId, @RequestHeader(value = "X-Internal-Token", required = false) String presented) {
        requireToken(presented);
        return ApiResponse.of(service.internalAccess(organizationId, userId));
    }
    @GetMapping("/{organizationId}/timezone")
    ApiResponse<String> timezone(@PathVariable UUID organizationId, @RequestHeader(value = "X-Internal-Token", required = false) String presented) {
        requireToken(presented);
        return ApiResponse.of(service.internalTimezone(organizationId));
    }
    private void requireToken(String presented) {
        byte[] candidate = presented == null ? new byte[0] : presented.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(token, candidate)) throw new ApiException(HttpStatus.FORBIDDEN, "invalid_internal_token", "Invalid internal token");
    }
}
