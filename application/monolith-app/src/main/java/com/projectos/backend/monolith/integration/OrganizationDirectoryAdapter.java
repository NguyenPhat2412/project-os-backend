package com.projectos.backend.monolith.integration;

import java.util.UUID;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.projectos.backend.organization.domain.OrganizationApplicationService;
import com.projectos.backend.organization.web.OrganizationController;
import com.projectos.backend.platform.organization.OrganizationDirectory;

/**
 * Bridges the organization module application service to shared in-process
 * ports.
 */
@Component
public class OrganizationDirectoryAdapter implements OrganizationDirectory {
    private final OrganizationApplicationService organizations;

    public OrganizationDirectoryAdapter(OrganizationApplicationService organizations) {
        this.organizations = organizations;
    }

    @Override
    public Employee employeeByUser(UUID organizationId, UUID userId) {
        return employee(organizations.employeeByUser(organizationId, userId));
    }

    @Override
    public Employee employee(UUID organizationId, UUID employeeId) {
        return employee(organizations.employee(organizationId, employeeId));
    }

    @Override
    public Access access(UUID organizationId, UUID userId) {
        var access = organizations.internalAccess(organizationId, userId);
        return new Access(access.timezone(), access.role());
    }

    @Override
    public String timezone(UUID organizationId) {
        return organizations.internalTimezone(organizationId);
    }

    @Override
    public AttendancePolicy attendancePolicy(UUID organizationId) {
        var policy = organizations.attendancePolicy(organizationId);
        return new AttendancePolicy(policy.configured(), policy.latitude(), policy.longitude(),
                policy.radiusMeters(), policy.officeName());
    }

    @Override
    public Set<EmployeeDetails> directReports(UUID organizationId, UUID supervisorId) {
        return organizations.directReports(organizationId, supervisorId, 0, 500).data().stream()
                .map(this::employeeDetails).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public EmployeeDetails employeeDetails(UUID organizationId, UUID employeeId) {
        return employeeDetails(organizations.employee(organizationId, employeeId));
    }

    private Employee employee(OrganizationController.EmployeeView value) {
        return new Employee(value.id(), value.organizationId(), value.supervisorId(), value.userId(), value.status(),
                value.code());
    }

    private EmployeeDetails employeeDetails(OrganizationController.EmployeeView value) {
        return new EmployeeDetails(value.id(), value.organizationId(), value.supervisorId(), value.userId(),
                value.fullName(), value.title(), value.status());
    }
}
