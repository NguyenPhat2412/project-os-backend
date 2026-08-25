package com.projectos.backend.activity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import com.projectos.backend.platform.project.ProjectAccessPort;

/** Compatibility bean backed by the in-process project port. */
@Component
@ConditionalOnMissingBean(ProjectScopeResolver.class)
class ProjectServiceScopeResolver implements ProjectScopeResolver {
    private final ProjectAccessPort projects;

    ProjectServiceScopeResolver(ObjectProvider<ProjectAccessPort> projects) {
        this.projects = projects.getIfAvailable();
    }

    @Override
    public Optional<UUID> organizationId(UUID projectId) {
        return projects == null ? Optional.empty() : projects.organizationId(projectId);
    }
}
