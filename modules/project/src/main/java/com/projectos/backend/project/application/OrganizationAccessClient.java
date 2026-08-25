package com.projectos.backend.project.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.organization.OrganizationDirectory;

/** Verifies the organization boundary before a project is created or accessed. */
@Component
class OrganizationAccessClient {
    private static final Set<String> PROJECT_MANAGERS = Set.of("OWNER", "ADMIN", "DEPARTMENT_MANAGER");

    private final boolean enabled;
    private final OrganizationDirectory organizations;

    OrganizationAccessClient(@Value("${app.rbac.enabled:true}") boolean enabled,
                             ObjectProvider<OrganizationDirectory> organizations) {
        this.enabled = enabled;
        this.organizations = organizations.getIfAvailable();
    }

    void requireMember(UUID organizationId, UUID actorId, boolean root) {
        if (root || !enabled) return;
        access(organizationId, actorId);
    }

    void requireProjectManager(UUID organizationId, UUID actorId, boolean root) {
        if (root || !enabled) return;
        String role = access(organizationId, actorId);
        if (!PROJECT_MANAGERS.contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "organization_project_manager_required",
                    "Organization manager access is required to create a project");
        }
    }

    private String access(UUID organizationId, UUID actorId) {
        try {
            if (organizations == null) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_service_unavailable",
                        "Organization directory is unavailable");
            }
            String role = organizations.access(organizationId, actorId).role();
            if (role == null || role.isBlank()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied");
            }
            return role.trim().toUpperCase(java.util.Locale.ROOT);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_service_unavailable",
                    "Organization service is unavailable");
        }
    }
}
