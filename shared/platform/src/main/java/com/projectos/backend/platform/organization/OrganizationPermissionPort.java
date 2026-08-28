package com.projectos.backend.platform.organization;

import java.util.UUID;

/** In-process port for organization-scoped capability checks. */
public interface OrganizationPermissionPort {
    void requirePermission(UUID organizationId, UUID actorId, boolean root, String permissionKey);
}
