package com.projectos.backend.organization.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
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
import com.projectos.backend.organization.domain.Department;
import com.projectos.backend.organization.domain.Employee;
import com.projectos.backend.organization.domain.Organization;
import com.projectos.backend.organization.domain.OrganizationApplicationService;
import com.projectos.backend.organization.domain.OrganizationMembership;
import com.projectos.backend.organization.domain.Position;
import com.projectos.backend.platform.api.ApiResponse;
import com.projectos.backend.platform.api.PageResponse;

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
    ApiResponse<OrganizationView> create(@Valid @RequestBody OrganizationRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.create(request, actor(jwt), root(jwt))); }
    @GetMapping("/{organizationId}")
    ApiResponse<OrganizationView> organization(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.organization(organizationId, actor(jwt), root(jwt))); }
    @PatchMapping("/{organizationId}")
    ApiResponse<OrganizationView> update(@PathVariable UUID organizationId, @Valid @RequestBody OrganizationPatch request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateOrganization(organizationId, request, actor(jwt), root(jwt))); }
    @GetMapping("/{organizationId}/company-policy")
    ApiResponse<CompanyPolicyView> companyPolicy(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.companyPolicy(organizationId, actor(jwt), root(jwt))); }
    @PutMapping("/{organizationId}/company-policy")
    ApiResponse<CompanyPolicyView> updateCompanyPolicy(@PathVariable UUID organizationId, @Valid @RequestBody CompanyPolicyRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateCompanyPolicy(organizationId, request, actor(jwt), root(jwt))); }
    @GetMapping("/{organizationId}/settings")
    ApiResponse<JsonNode> settings(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.settings(organizationId, actor(jwt), root(jwt))); }
    @PostMapping("/{organizationId}/settings")
    ApiResponse<JsonNode> updateSettings(@PathVariable UUID organizationId, @RequestBody JsonNode request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateSettings(organizationId, request, actor(jwt), root(jwt))); }

    @GetMapping("/{organizationId}/departments")
    PageResponse<DepartmentView> departments(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, @AuthenticationPrincipal Jwt jwt) { return service.departments(organizationId, page, size, actor(jwt), root(jwt)); }
    @PostMapping("/{organizationId}/departments") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<DepartmentView> createDepartment(@PathVariable UUID organizationId, @Valid @RequestBody DepartmentRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.createDepartment(organizationId, request, actor(jwt), root(jwt))); }
    @PatchMapping("/{organizationId}/departments/{departmentId}")
    ApiResponse<DepartmentView> updateDepartment(@PathVariable UUID organizationId, @PathVariable UUID departmentId, @Valid @RequestBody DepartmentPatch request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateDepartment(organizationId, departmentId, request, actor(jwt), root(jwt))); }
    @DeleteMapping("/{organizationId}/departments/{departmentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDepartment(@PathVariable UUID organizationId, @PathVariable UUID departmentId, @AuthenticationPrincipal Jwt jwt) { service.deleteDepartment(organizationId, departmentId, actor(jwt), root(jwt)); }

    @GetMapping("/{organizationId}/positions")
    PageResponse<PositionView> positions(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, @AuthenticationPrincipal Jwt jwt) { return service.positions(organizationId, page, size, actor(jwt), root(jwt)); }
    @PostMapping("/{organizationId}/positions") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PositionView> createPosition(@PathVariable UUID organizationId, @Valid @RequestBody PositionRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.createPosition(organizationId, request, actor(jwt), root(jwt))); }
    @PatchMapping("/{organizationId}/positions/{positionId}")
    ApiResponse<PositionView> updatePosition(@PathVariable UUID organizationId, @PathVariable UUID positionId, @Valid @RequestBody PositionPatch request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updatePosition(organizationId, positionId, request, actor(jwt), root(jwt))); }
    @DeleteMapping("/{organizationId}/positions/{positionId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePosition(@PathVariable UUID organizationId, @PathVariable UUID positionId, @AuthenticationPrincipal Jwt jwt) { service.deletePosition(organizationId, positionId, actor(jwt), root(jwt)); }

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
    @GetMapping("/{organizationId}/employees/{employeeId}/compensation")
    ApiResponse<CompensationView> compensation(@PathVariable UUID organizationId, @PathVariable UUID employeeId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.compensation(organizationId, employeeId, actor(jwt), root(jwt))); }
    @PutMapping("/{organizationId}/employees/{employeeId}/compensation")
    ApiResponse<CompensationView> updateCompensation(@PathVariable UUID organizationId, @PathVariable UUID employeeId, @Valid @RequestBody CompensationRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateCompensation(organizationId, employeeId, request, actor(jwt), root(jwt))); }

    @GetMapping("/{organizationId}/members")
    PageResponse<MembershipView> memberships(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, @AuthenticationPrincipal Jwt jwt) { return service.memberships(organizationId, page, size, actor(jwt), root(jwt)); }
    @PutMapping("/{organizationId}/members")
    ApiResponse<MembershipView> upsertMembership(@PathVariable UUID organizationId, @Valid @RequestBody MembershipRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.upsertMembership(organizationId, request, actor(jwt), root(jwt))); }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }

    public record OrganizationRequest(@NotBlank @Size(max = 150) String name, @Pattern(regexp = "[a-zA-Z0-9-]{1,80}", message = "slug must use letters, numbers or hyphens") String slug, @Size(max = 80) String timezone,
                                      String code, String nameVi, String nameEn, String shortName, String taxCode,
                                      String foundedDate, String legalRepresentative, String representativeTitle,
                                      String charterCapital, String businessType, String industry, String hotline,
                                      String email, String website, String headquartersAddress, String loginSubdomain,
                                      String domainTail, String adminEmail) {}
    public record OrganizationPatch(@Size(min = 1, max = 150) String name, @Pattern(regexp = "[a-zA-Z0-9-]{1,80}", message = "slug must use letters, numbers or hyphens") String slug, @Size(max = 80) String timezone, String status,
                                    String code, String nameVi, String nameEn, String shortName, String taxCode,
                                    String foundedDate, String legalRepresentative, String representativeTitle,
                                    String charterCapital, String businessType, String industry, String hotline,
                                    String email, String website, String headquartersAddress, String loginSubdomain,
                                    String domainTail, String adminEmail) {}
    public record CompanyPolicyRequest(@NotNull LocalTime morningStart, @NotNull LocalTime morningEnd,
                                       @NotNull LocalTime afternoonStart, @NotNull LocalTime afternoonEnd,
                                       @NotEmpty List<@NotBlank @Size(max = 500) String> rules) {}
    public record DepartmentRequest(@NotBlank @Size(max = 150) String name, UUID parentId) {}
    public record DepartmentPatch(@Size(min = 1, max = 150) String name, UUID parentId) {}
    public record PositionRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 200) String title,
                                  @Size(max = 80) String jobLevel, @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal standardSalary,
                                  UUID departmentId, @Size(max = 4000) String description) {}
    public record PositionPatch(@Size(max = 80) String code, @Size(max = 200) String title, @Size(max = 80) String jobLevel,
                                @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal standardSalary,
                                UUID departmentId, @Size(max = 4000) String description, String status) {}
    public record EmployeeRequest(@NotBlank @Size(max = 150) String fullName, @NotBlank @Email @Size(max = 254) String email, @Size(max = 50) String code, @Size(max = 50) String phone, @Size(max = 150) String title, @Size(max = 4000) String notes, UUID departmentId, UUID supervisorId) {}
    public record EmployeePatch(@Size(min = 1, max = 150) String fullName, @Email @Size(max = 254) String email, @Size(max = 50) String code, @Size(max = 50) String phone, @Size(max = 150) String title, @Size(max = 4000) String notes, UUID departmentId, UUID supervisorId, String status) {}
    public record CompensationRequest(@NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal monthlyAmount) {}
    public record LinkUserRequest(@NotNull UUID userId) {}
    public record MembershipRequest(@NotNull UUID userId, String role, String status,
                                    @Size(max = 150) String fullName, @Email @Size(max = 254) String email) {}
    public record OrganizationView(UUID id, String name, String slug, String timezone, String status, UUID createdBy, Instant createdAt, Instant updatedAt,
                                   String code, String nameVi, String nameEn, String shortName, String taxCode,
                                   String foundedDate, String legalRepresentative, String representativeTitle,
                                   String charterCapital, String businessType, String industry, String hotline,
                                   String email, String website, String headquartersAddress, String loginSubdomain,
                                   String domainTail, String adminEmail, JsonNode details) {
        public static OrganizationView from(Organization value) {
            JsonNode details = value.getDetails();
            return new OrganizationView(value.getId(), value.getName(), value.getSlug(), value.getTimezone(), value.getStatus().name().toLowerCase(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt(),
                    value.getCode(), value.getNameVi(), value.getNameEn(), value.getShortName(), value.getTaxCode(),
                    text(details, "foundedDate"), value.getLegalRepresentative(), value.getRepresentativeTitle(),
                    text(details, "charterCapital"), text(details, "businessType"), text(details, "industry"), value.getHotline(),
                    value.getEmail(), value.getWebsite(), value.getHeadquartersAddress(), text(details, "loginSubdomain"),
                    text(details, "domainTail"), text(details, "adminEmail"), details);
        }
        private static String text(JsonNode source, String field) {
            JsonNode value = source == null ? null : source.get(field);
            return value == null || value.isNull() ? null : value.asText();
        }
    }
    public record CompanyPolicyView(UUID organizationId, String morningStart, String morningEnd, String afternoonStart, String afternoonEnd, List<String> rules, Instant updatedAt) { public static CompanyPolicyView from(com.projectos.backend.organization.domain.CompanyPolicy value) { return new CompanyPolicyView(value.getOrganizationId(), value.getMorningStart().toString(), value.getMorningEnd().toString(), value.getAfternoonStart().toString(), value.getAfternoonEnd().toString(), value.getRules(), value.getUpdatedAt()); } }
    public record DepartmentView(UUID id, UUID organizationId, UUID parentId, String name, Instant createdAt, Instant updatedAt) { public static DepartmentView from(Department value) { return new DepartmentView(value.getId(), value.getOrganizationId(), value.getParentId(), value.getName(), value.getCreatedAt(), value.getUpdatedAt()); } }
    public record PositionView(UUID id, UUID organizationId, UUID departmentId, String code, String title, String jobLevel,
                               BigDecimal standardSalary, String status, String description, UUID createdBy, UUID updatedBy,
                               Instant createdAt, Instant updatedAt) {
        public static PositionView from(Position value) { return new PositionView(value.getId(), value.getOrganizationId(), value.getDepartmentId(), value.getCode(), value.getTitle(),
                value.getJobLevel(), value.getStandardSalary(), value.getStatus().name().toLowerCase(Locale.ROOT), value.getDescription(), value.getCreatedBy(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt()); }
    }
    public record EmployeeView(UUID id, UUID organizationId, UUID departmentId, UUID supervisorId, UUID userId, String code, String fullName, String email, String phone, String title, String notes, String status, Instant createdAt, Instant updatedAt) { public static EmployeeView from(Employee value) { return new EmployeeView(value.getId(), value.getOrganizationId(), value.getDepartmentId(), value.getSupervisorId(), value.getUserId(), value.getCode(), value.getFullName(), value.getEmail(), value.getPhone(), value.getTitle(), value.getNotes(), value.getStatus().name().toLowerCase(), value.getCreatedAt(), value.getUpdatedAt()); } }
    public record CompensationView(UUID employeeId, BigDecimal monthlyAmount, Instant updatedAt) { public static CompensationView from(com.projectos.backend.organization.domain.EmployeeCompensation value) { return new CompensationView(value.getEmployeeId(), value.getMonthlyAmount(), value.getUpdatedAt()); } }
    public record MembershipView(UUID id, UUID organizationId, UUID userId, String role, String status, Instant createdAt, Instant updatedAt) { public static MembershipView from(OrganizationMembership value) { return new MembershipView(value.getId(), value.getOrganizationId(), value.getUserId(), value.getRole().name().toLowerCase(), value.getStatus().name().toLowerCase(), value.getCreatedAt(), value.getUpdatedAt()); } }
}
