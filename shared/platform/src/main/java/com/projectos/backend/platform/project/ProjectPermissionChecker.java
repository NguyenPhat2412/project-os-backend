package com.projectos.backend.platform.project;

import java.util.UUID;

/** In-process project authorization port. */
public interface ProjectPermissionChecker {
    boolean allowed(UUID projectId, UUID actorId, String resource, String action);
}
