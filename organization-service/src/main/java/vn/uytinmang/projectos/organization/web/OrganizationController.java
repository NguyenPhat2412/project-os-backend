package vn.uytinmang.projectos.organization.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.organization.domain.Department;
import vn.uytinmang.projectos.organization.domain.Employee;
import vn.uytinmang.projectos.organization.domain.Organization;
import vn.uytinmang.projectos.organization.domain.OrganizationApplicationService;
import vn.uytinmang.projectos.organization.domain.OrganizationMembership;
import vn.uytinmang.projectos.platform.api.ApiResponse;
import vn.uytinmang.projectos.platform.api.PageResponse;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationApplicationService service;
    public OrganizationController(OrganizationApplicationService service) { this.service = service; }

    @GetMapping
    PageResponse<OrganizationView> organizations(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        return service.organizations(page, size, actor(jwt), root(jwt));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<OrganizationView> create(@Valid @RequestBody OrganizationRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.create(request, actor(jwt))); }
    @GetMapping("/{organizationId}")
    ApiResponse<OrganizationView> organization(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.organization(organizationId, actor(jwt), root(jwt))); }
    @PatchMapping("/{organizationId}")
    ApiResponse<OrganizationView> update(@PathVariable UUID organizationId, @Valid @RequestBody OrganizationPatch request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateOrganization(organizationId, request, actor(jwt), root(jwt))); }

    @GetMapping("/{organizationId}/departments")
    PageResponse<DepartmentView> departments(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, @AuthenticationPrincipal Jwt jwt) { return service.departments(organizationId, page, size, actor(jwt), root(jwt)); }
    @PostMapping("/{organizationId}/departments") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<DepartmentView> createDepartment(@PathVariable UUID organizationId, @Valid @RequestBody DepartmentRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.createDepartment(organizationId, request, actor(jwt), root(jwt))); }
    @PatchMapping("/{organizationId}/departments/{departmentId}")
    ApiResponse<DepartmentView> updateDepartment(@PathVariable UUID organizationId, @PathVariable UUID departmentId, @Valid @RequestBody DepartmentPatch request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateDepartment(organizationId, departmentId, request, actor(jwt), root(jwt))); }
    @DeleteMapping("/{organizationId}/departments/{departmentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDepartment(@PathVariable UUID organizationId, @PathVariable UUID departmentId, @AuthenticationPrincipal Jwt jwt) { service.deleteDepartment(organizationId, departmentId, actor(jwt), root(jwt)); }

    @GetMapping("/{organizationId}/employees")
    PageResponse<EmployeeView> employees(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, @AuthenticationPrincipal Jwt jwt) { return service.employees(organizationId, page, size, actor(jwt), root(jwt)); }
    @PostMapping("/{organizationId}/employees") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<EmployeeView> createEmployee(@PathVariable UUID organizationId, @Valid @RequestBody EmployeeRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.createEmployee(organizationId, request, actor(jwt), root(jwt))); }
    @PatchMapping("/{organizationId}/employees/{employeeId}")
    ApiResponse<EmployeeView> updateEmployee(@PathVariable UUID organizationId, @PathVariable UUID employeeId, @Valid @RequestBody EmployeePatch request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateEmployee(organizationId, employeeId, request, actor(jwt), root(jwt))); }
    @PostMapping("/{organizationId}/employees/{employeeId}/link-user")
    ApiResponse<EmployeeView> linkUser(@PathVariable UUID organizationId, @PathVariable UUID employeeId, @Valid @RequestBody LinkUserRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.linkUser(organizationId, employeeId, request.userId(), actor(jwt), root(jwt))); }
    @DeleteMapping("/{organizationId}/employees/{employeeId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEmployee(@PathVariable UUID organizationId, @PathVariable UUID employeeId, @AuthenticationPrincipal Jwt jwt) { service.deleteEmployee(organizationId, employeeId, actor(jwt), root(jwt)); }

    @GetMapping("/{organizationId}/members")
    PageResponse<MembershipView> memberships(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, @AuthenticationPrincipal Jwt jwt) { return service.memberships(organizationId, page, size, actor(jwt), root(jwt)); }
    @PutMapping("/{organizationId}/members")
    ApiResponse<MembershipView> upsertMembership(@PathVariable UUID organizationId, @Valid @RequestBody MembershipRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.upsertMembership(organizationId, request, actor(jwt), root(jwt))); }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }

    public record OrganizationRequest(@NotBlank @Size(max = 150) String name, @Pattern(regexp = "[a-zA-Z0-9-]{1,80}", message = "slug must use letters, numbers or hyphens") String slug, @Size(max = 80) String timezone) {}
    public record OrganizationPatch(@Size(min = 1, max = 150) String name, @Pattern(regexp = "[a-zA-Z0-9-]{1,80}", message = "slug must use letters, numbers or hyphens") String slug, @Size(max = 80) String timezone, String status) {}
    public record DepartmentRequest(@NotBlank @Size(max = 150) String name, UUID parentId) {}
    public record DepartmentPatch(@Size(min = 1, max = 150) String name, UUID parentId) {}
    public record EmployeeRequest(@NotBlank @Size(max = 150) String fullName, @NotBlank @Email @Size(max = 254) String email, @Size(max = 150) String title, UUID departmentId, UUID supervisorId) {}
    public record EmployeePatch(@Size(min = 1, max = 150) String fullName, @Email @Size(max = 254) String email, @Size(max = 150) String title, UUID departmentId, UUID supervisorId, String status) {}
    public record LinkUserRequest(@NotNull UUID userId) {}
    public record MembershipRequest(@NotNull UUID userId, String role, String status) {}
    public record OrganizationView(UUID id, String name, String slug, String timezone, String status, UUID createdBy, Instant createdAt, Instant updatedAt) { public static OrganizationView from(Organization value) { return new OrganizationView(value.getId(), value.getName(), value.getSlug(), value.getTimezone(), value.getStatus().name().toLowerCase(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt()); } }
    public record DepartmentView(UUID id, UUID organizationId, UUID parentId, String name, Instant createdAt, Instant updatedAt) { public static DepartmentView from(Department value) { return new DepartmentView(value.getId(), value.getOrganizationId(), value.getParentId(), value.getName(), value.getCreatedAt(), value.getUpdatedAt()); } }
    public record EmployeeView(UUID id, UUID organizationId, UUID departmentId, UUID supervisorId, UUID userId, String fullName, String email, String title, String status, Instant createdAt, Instant updatedAt) { public static EmployeeView from(Employee value) { return new EmployeeView(value.getId(), value.getOrganizationId(), value.getDepartmentId(), value.getSupervisorId(), value.getUserId(), value.getFullName(), value.getEmail(), value.getTitle(), value.getStatus().name().toLowerCase(), value.getCreatedAt(), value.getUpdatedAt()); } }
    public record MembershipView(UUID id, UUID organizationId, UUID userId, String role, String status, Instant createdAt, Instant updatedAt) { public static MembershipView from(OrganizationMembership value) { return new MembershipView(value.getId(), value.getOrganizationId(), value.getUserId(), value.getRole().name().toLowerCase(), value.getStatus().name().toLowerCase(), value.getCreatedAt(), value.getUpdatedAt()); } }
}
