package com.projectos.backend.monolith.integration;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.projectos.backend.activity.ProjectScopeResolver;
import com.projectos.backend.platform.project.ProjectAccessPort;
import com.projectos.backend.platform.project.ProjectPermissionChecker;
import com.projectos.backend.project.domain.ProjectRepository;
import com.projectos.backend.project.permission.ProjectPermissionService;
import com.projectos.backend.resource.ResourceRecordRepository;
import org.springframework.data.domain.PageRequest;

/** Monolith adapter that keeps project scope lookups inside the process. */
@Component
public class ProjectAccessAdapter implements ProjectAccessPort, ProjectPermissionChecker, ProjectScopeResolver {
    private final ProjectRepository projects;
    private final ProjectPermissionService permissions;
    private final ResourceRecordRepository records;

    public ProjectAccessAdapter(ProjectRepository projects, ProjectPermissionService permissions,
                                ResourceRecordRepository records) {
        this.projects = projects;
        this.permissions = permissions;
        this.records = records;
    }

    @Override
    public Optional<UUID> organizationId(UUID projectId) {
        return projects.findById(projectId).map(project -> project.getOrganizationId());
    }

    @Override
    public boolean allowed(UUID projectId, UUID actorId, String resource, String action) {
        return permissions.allowed(projectId, actorId, resource, action);
    }

    @Override
    public Set<UUID> memberIds(UUID projectId, UUID actorId, boolean rootAdmin) {
        if (!rootAdmin && !permissions.allowed(projectId, actorId, "members", "read")) {
            throw new com.projectos.backend.platform.api.ApiException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "project_access_denied", "Project access denied");
        }
        return records.findAllByProjectIdAndResourceType(projectId, "members", PageRequest.of(0, 500))
                .getContent().stream()
                .map(record -> first(record.getPayload(), "uid", "memberId", "userId"))
                .filter(java.util.Objects::nonNull)
                .map(UUID::fromString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String first(tools.jackson.databind.JsonNode payload, String... fields) {
        for (String field : fields) if (payload.hasNonNull(field)) return payload.get(field).asText();
        return null;
    }
}
