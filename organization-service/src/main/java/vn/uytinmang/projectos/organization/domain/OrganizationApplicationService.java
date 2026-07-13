package vn.uytinmang.projectos.organization.domain;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.organization.web.OrganizationController;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.PageResponse;

@Service
public class OrganizationApplicationService {
    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final OrganizationMembershipRepository memberships;

    OrganizationApplicationService(OrganizationRepository organizations, DepartmentRepository departments,
                                   EmployeeRepository employees, OrganizationMembershipRepository memberships) {
        this.organizations = organizations;
        this.departments = departments;
        this.employees = employees;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.OrganizationView> organizations(int page, int size, UUID actor, boolean root) {
        PageRequest pageable = page(page, size);
        var result = (root ? organizations.findAll(pageable) : organizations.findAccessible(actor, pageable))
                .map(OrganizationController.OrganizationView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OrganizationController.OrganizationView create(OrganizationController.OrganizationRequest request, UUID actor) {
        String slug = slug(request.slug(), request.name());
        if (organizations.findBySlug(slug).isPresent()) throw conflict("organization_slug_exists", "Organization slug already exists");
        Organization organization = organizations.save(new Organization(request.name().trim(), slug, timezone(request.timezone()), actor));
        memberships.save(new OrganizationMembership(organization.getId(), actor, OrganizationMembership.Role.OWNER));
        return OrganizationController.OrganizationView.from(organization);
    }

    @Transactional(readOnly = true)
    public OrganizationController.OrganizationView organization(UUID id, UUID actor, boolean root) {
        requireMember(id, actor, root);
        return OrganizationController.OrganizationView.from(requireOrganization(id));
    }

    @Transactional
    public OrganizationController.OrganizationView updateOrganization(UUID id, OrganizationController.OrganizationPatch request, UUID actor, boolean root) {
        requireAdmin(id, actor, root);
        Organization organization = requireOrganization(id);
        String nextSlug = request.slug() == null ? null : slug(request.slug(), organization.getName());
        if (nextSlug != null && !nextSlug.equals(organization.getSlug()) && organizations.findBySlug(nextSlug).isPresent()) {
            throw conflict("organization_slug_exists", "Organization slug already exists");
        }
        organization.update(clean(request.name()), nextSlug, request.timezone() == null ? null : timezone(request.timezone()), status(request.status(), Organization.Status.class));
        return OrganizationController.OrganizationView.from(organization);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.DepartmentView> departments(UUID organizationId, int page, int size, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        var result = departments.findByOrganizationId(organizationId, page(page, size)).map(OrganizationController.DepartmentView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OrganizationController.DepartmentView createDepartment(UUID organizationId, OrganizationController.DepartmentRequest request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        validateParent(organizationId, request.parentId(), null);
        return OrganizationController.DepartmentView.from(departments.save(new Department(organizationId, request.parentId(), request.name().trim())));
    }

    @Transactional
    public OrganizationController.DepartmentView updateDepartment(UUID organizationId, UUID departmentId, OrganizationController.DepartmentPatch request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Department department = requireDepartment(organizationId, departmentId);
        if (request.parentId() != null) validateParent(organizationId, request.parentId(), departmentId);
        department.update(request.parentId(), clean(request.name()));
        return OrganizationController.DepartmentView.from(department);
    }

    @Transactional
    public void deleteDepartment(UUID organizationId, UUID departmentId, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Department department = requireDepartment(organizationId, departmentId);
        if (employees.countByOrganizationIdAndDepartmentId(organizationId, departmentId) > 0) {
            throw conflict("department_not_empty", "Move employees before deleting this department");
        }
        departments.delete(department);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.EmployeeView> employees(UUID organizationId, int page, int size, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        var result = employees.findByOrganizationId(organizationId, page(page, size)).map(OrganizationController.EmployeeView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OrganizationController.EmployeeView createEmployee(UUID organizationId, OrganizationController.EmployeeRequest request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        validateDepartment(organizationId, request.departmentId());
        validateSupervisor(organizationId, request.supervisorId(), null);
        Employee employee = new Employee(organizationId, request.departmentId(), request.supervisorId(), request.fullName().trim(), email(request.email()), clean(request.title()));
        return OrganizationController.EmployeeView.from(employees.save(employee));
    }

    @Transactional
    public OrganizationController.EmployeeView updateEmployee(UUID organizationId, UUID employeeId, OrganizationController.EmployeePatch request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Employee employee = requireEmployee(organizationId, employeeId);
        if (request.departmentId() != null) validateDepartment(organizationId, request.departmentId());
        if (request.supervisorId() != null) validateSupervisor(organizationId, request.supervisorId(), employeeId);
        employee.update(request.departmentId(), request.supervisorId(), clean(request.fullName()), request.email() == null ? null : email(request.email()), clean(request.title()), status(request.status(), Employee.Status.class));
        return OrganizationController.EmployeeView.from(employee);
    }

    @Transactional
    public OrganizationController.EmployeeView linkUser(UUID organizationId, UUID employeeId, UUID userId, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        employees.findByOrganizationIdAndUserId(organizationId, userId).filter(found -> !found.getId().equals(employeeId)).ifPresent(found -> {
            throw conflict("employee_user_already_linked", "User is already linked to another employee");
        });
        Employee employee = requireEmployee(organizationId, employeeId);
        employee.linkUser(userId);
        memberships.findByOrganizationIdAndUserId(organizationId, userId).orElseGet(() -> memberships.save(new OrganizationMembership(organizationId, userId, OrganizationMembership.Role.MEMBER)));
        return OrganizationController.EmployeeView.from(employee);
    }

    @Transactional
    public void deleteEmployee(UUID organizationId, UUID employeeId, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        employees.delete(requireEmployee(organizationId, employeeId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.MembershipView> memberships(UUID organizationId, int page, int size, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        var result = memberships.findByOrganizationId(organizationId, page(page, size)).map(OrganizationController.MembershipView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OrganizationController.MembershipView upsertMembership(UUID organizationId, OrganizationController.MembershipRequest request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, request.userId())
                .orElseGet(() -> new OrganizationMembership(organizationId, request.userId(), role(request.role())));
        membership.update(role(request.role()), membershipStatus(request.status()));
        return OrganizationController.MembershipView.from(memberships.save(membership));
    }

    @Transactional(readOnly = true)
    public OrganizationController.EmployeeView employeeByUser(UUID organizationId, UUID userId) {
        return OrganizationController.EmployeeView.from(employees.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "employee_not_found", "Employee not found")));
    }

    @Transactional(readOnly = true)
    public OrganizationController.EmployeeView employee(UUID organizationId, UUID employeeId) {
        return OrganizationController.EmployeeView.from(requireEmployee(organizationId, employeeId));
    }

    @Transactional(readOnly = true)
    public InternalAccess internalAccess(UUID organizationId, UUID userId) {
        Organization organization = requireOrganization(organizationId);
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, userId)
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied"));
        return new InternalAccess(organization.getTimezone(), membership.getRole().name());
    }

    @Transactional(readOnly = true)
    public String internalTimezone(UUID organizationId) { return requireOrganization(organizationId).getTimezone(); }

    public record InternalAccess(String timezone, String role) {}

    private void requireMember(UUID organizationId, UUID actor, boolean root) {
        if (root) return;
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, actor)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied"));
        if (membership.getStatus() != OrganizationMembership.Status.ACTIVE) throw new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied");
    }
    private void requireAdmin(UUID organizationId, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        if (root) return;
        OrganizationMembership.Role role = memberships.findByOrganizationIdAndUserId(organizationId, actor).orElseThrow().getRole();
        if (role != OrganizationMembership.Role.OWNER && role != OrganizationMembership.Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "organization_admin_required", "Organization admin access is required");
    }
    private Organization requireOrganization(UUID id) { return organizations.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "organization_not_found", "Organization not found")); }
    private Department requireDepartment(UUID organizationId, UUID id) { return departments.findById(id).filter(value -> value.getOrganizationId().equals(organizationId)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "department_not_found", "Department not found")); }
    private Employee requireEmployee(UUID organizationId, UUID id) { return employees.findById(id).filter(value -> value.getOrganizationId().equals(organizationId)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "employee_not_found", "Employee not found")); }
    private void validateDepartment(UUID organizationId, UUID departmentId) { if (departmentId != null) requireDepartment(organizationId, departmentId); }
    private void validateSupervisor(UUID organizationId, UUID supervisorId, UUID employeeId) {
        if (supervisorId == null) return;
        UUID cursor = supervisorId;
        while (cursor != null) {
            if (cursor.equals(employeeId)) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_supervisor", "Supervisor hierarchy cannot contain a cycle");
            cursor = requireEmployee(organizationId, cursor).getSupervisorId();
        }
    }
    private void validateParent(UUID organizationId, UUID parentId, UUID departmentId) {
        if (parentId == null) return;
        UUID cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(departmentId)) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_department_parent", "Department hierarchy cannot contain a cycle");
            cursor = requireDepartment(organizationId, cursor).getParentId();
        }
    }
    private PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "page must be >= 0 and size must be between 1 and 100");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    }
    private String slug(String input, String fallback) {
        String value = input == null || input.isBlank() ? fallback : input;
        value = value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (value.isBlank() || value.length() > 80) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_slug", "Invalid organization slug");
        return value;
    }
    private String timezone(String value) { try { return ZoneId.of(value == null || value.isBlank() ? "Asia/Ho_Chi_Minh" : value).getId(); } catch (ZoneRulesException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_timezone", "Invalid timezone"); } }
    private String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private OrganizationMembership.Role role(String value) { try { return OrganizationMembership.Role.valueOf(value == null ? "MEMBER" : value.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_role", "Invalid organization role"); } }
    private OrganizationMembership.Status membershipStatus(String value) { try { return value == null ? OrganizationMembership.Status.ACTIVE : OrganizationMembership.Status.valueOf(value.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_membership_status", "Invalid membership status"); } }
    private <T extends Enum<T>> T status(String value, Class<T> type) { try { return value == null ? null : Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_status", "Invalid status"); } }
}
