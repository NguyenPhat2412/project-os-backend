package com.projectos.backend.activity;

import java.util.Optional;
import java.util.UUID;

public interface ProjectScopeResolver {
    Optional<UUID> organizationId(UUID projectId);
}
