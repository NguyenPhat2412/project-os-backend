package com.projectos.backend.identity.user;

import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.project.ProjectAccessPort;

@Component
class ProjectDirectoryClient {
    private final ProjectAccessPort projects;

    ProjectDirectoryClient(ObjectProvider<ProjectAccessPort> projects) {
        this.projects = projects.getIfAvailable();
    }

    Set<UUID> memberIds(UUID projectId, Jwt jwt) {
        if (projects == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "project_service_unavailable",
                    "Project directory is unavailable");
        }
        UUID actorId;
        try {
            actorId = UUID.fromString(jwt.getClaimAsString("uid"));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_actor", "Invalid authenticated actor");
        }
        boolean root = "ROOT_ADMIN".equals(jwt.getClaimAsString("role"));
        return projects.memberIds(projectId, actorId, root);
    }
}
