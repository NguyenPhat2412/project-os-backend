package com.projectos.backend.work;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.organization.OrganizationDirectory;
import com.projectos.backend.platform.project.ProjectPermissionChecker;

@Component
class WorkAccessClient {
    private final boolean enabled;
    private final ProjectPermissionChecker projects;
    private final OrganizationDirectory organizations;

    WorkAccessClient(@Value("${app.rbac.enabled:true}") boolean enabled,
                     ObjectProvider<ProjectPermissionChecker> projects,
                     ObjectProvider<OrganizationDirectory> organizations) {
        this.enabled = enabled;
        this.projects = projects.getIfAvailable();
        this.organizations = organizations.getIfAvailable();
    }

    void requireProject(UUID projectId, UUID actorId, String resource, String action, boolean root) {
        if (root || !enabled) return;
        if (projects == null) throw unavailable("permission_service_unavailable", "Project permission service is unavailable");
        try {
            if (!projects.allowed(projectId, actorId, resource, action)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "permission_denied", "Project access denied");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable("permission_service_unavailable", "Project permission service is unavailable");
        }
    }

    Set<UUID> directReportUsers(UUID organizationId, UUID managerUserId, boolean root) {
        if (root || !enabled) return Set.of();
        OrganizationDirectory.Employee manager = employeeByUser(organizationId, managerUserId);
        if (manager == null) return Set.of();
        try {
            return organizations.directReports(organizationId, manager.id()).stream()
                    .map(OrganizationDirectory.EmployeeDetails::userId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception exception) {
            throw unavailable("organization_service_unavailable", "Organization directory is unavailable");
        }
    }

    ReportRecipient reportRecipient(UUID organizationId, UUID actorId, boolean root) {
        if (root || !enabled) return null;
        OrganizationDirectory.Employee employee = employeeByUser(organizationId, actorId);
        if (employee == null || employee.supervisorId() == null) return null;
        try {
            OrganizationDirectory.EmployeeDetails supervisor = organizations.employeeDetails(organizationId, employee.supervisorId());
            if (supervisor == null || supervisor.userId() == null) return null;
            return new ReportRecipient(supervisor.userId(), supervisor.fullName(), supervisor.title());
        } catch (Exception exception) {
            throw unavailable("organization_service_unavailable", "Organization directory is unavailable");
        }
    }

    private OrganizationDirectory.Employee employeeByUser(UUID organizationId, UUID userId) {
        if (organizations == null) throw unavailable("organization_service_unavailable", "Organization directory is unavailable");
        return organizations.employeeByUser(organizationId, userId);
    }

    private ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    record ReportRecipient(UUID userId, String fullName, String title) {}
}
