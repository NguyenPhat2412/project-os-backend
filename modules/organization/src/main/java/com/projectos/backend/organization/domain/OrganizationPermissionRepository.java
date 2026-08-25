package com.projectos.backend.organization.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationPermissionRepository extends JpaRepository<OrganizationPermission, UUID> {
    List<OrganizationPermission> findByOrganizationId(UUID organizationId);
    void deleteByOrganizationId(UUID organizationId);
    void deleteByOrganizationIdAndPermissionKeyStartingWith(UUID organizationId, String permissionPrefix);
}
