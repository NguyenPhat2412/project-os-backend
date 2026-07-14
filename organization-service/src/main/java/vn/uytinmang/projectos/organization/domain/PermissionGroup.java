package vn.uytinmang.projectos.organization.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "permission_groups")
class PermissionGroup {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false) private String name;
    private String description;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "permission_group_modules", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "module_key", nullable = false)
    private Set<String> modules = new LinkedHashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PermissionGroup() {}

    PermissionGroup(UUID organizationId, String name, String description, Set<String> modules) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.description = description;
        this.modules = new LinkedHashSet<>(modules);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void update(String name, String description, Set<String> modules) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (modules != null) this.modules = new LinkedHashSet<>(modules);
        this.updatedAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getOrganizationId() { return organizationId; }
    String getName() { return name; }
    String getDescription() { return description; }
    Set<String> getModules() { return Set.copyOf(modules); }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
