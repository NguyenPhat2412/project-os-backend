package com.projectos.backend.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_permissions")
class OrganizationPermission {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "permission_key", nullable = false) private String permissionKey;
    @Column(name = "role_key", nullable = false) private String roleKey;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected OrganizationPermission() {}

    OrganizationPermission(UUID organizationId, String permissionKey, String roleKey) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.permissionKey = permissionKey;
        this.roleKey = roleKey;
        this.createdAt = Instant.now();
    }

    UUID getOrganizationId() { return organizationId; }
    String getPermissionKey() { return permissionKey; }
    String getRoleKey() { return roleKey; }
}
