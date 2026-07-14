package vn.uytinmang.projectos.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permission_group_members")
class PermissionGroupMember {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "group_id", nullable = false) private UUID groupId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected PermissionGroupMember() {}
    PermissionGroupMember(UUID organizationId, UUID groupId, UUID userId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.groupId = groupId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    UUID getGroupId() { return groupId; }
    UUID getUserId() { return userId; }
}
