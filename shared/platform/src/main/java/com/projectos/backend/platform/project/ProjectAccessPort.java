package com.projectos.backend.platform.project;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-process read port for project scope and authorization data. */
public interface ProjectAccessPort {
    Optional<UUID> organizationId(UUID projectId);

    Set<UUID> memberIds(UUID projectId, UUID actorId, boolean rootAdmin);
}
