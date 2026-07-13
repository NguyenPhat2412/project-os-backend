package vn.uytinmang.projectos.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "departments")
public class Department {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "parent_id") private UUID parentId;
    @Column(nullable = false) private String name;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Department() {}
    public Department(UUID organizationId, UUID parentId, String name) { this.organizationId = organizationId; this.parentId = parentId; this.name = name; }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void update(UUID parentId, String name) { if (parentId != null) this.parentId = parentId; if (name != null) this.name = name; }
    public UUID getId() { return id; } public UUID getOrganizationId() { return organizationId; } public UUID getParentId() { return parentId; }
    public String getName() { return name; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
