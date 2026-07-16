package vn.uytinmang.projectos.activity;

import java.util.Optional;
import java.util.UUID;

interface ProjectScopeResolver {
    Optional<UUID> organizationId(UUID projectId);
}
