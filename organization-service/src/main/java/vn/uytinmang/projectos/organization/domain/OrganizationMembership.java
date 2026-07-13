package vn.uytinmang.projectos.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_memberships")
public class OrganizationMembership {
    public enum Role { OWNER, ADMIN, MEMBER }
    public enum Status { ACTIVE, DISABLED }
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected OrganizationMembership() {}
    public OrganizationMembership(UUID organizationId, UUID userId, Role role) {
        this.organizationId = organizationId; this.userId = userId; this.role = role; this.status = Status.ACTIVE;
    }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void update(Role role, Status status) { if (role != null) this.role = role; if (status != null) this.status = status; }
    public UUID getId() { return id; } public UUID getOrganizationId() { return organizationId; } public UUID getUserId() { return userId; }
    public Role getRole() { return role; } public Status getStatus() { return status; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
