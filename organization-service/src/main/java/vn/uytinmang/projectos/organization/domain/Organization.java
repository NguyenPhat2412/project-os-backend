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
@Table(name = "organizations")
public class Organization {
    public enum Status { ACTIVE, DISABLED }

    @Id private UUID id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String timezone;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Organization() {}
    public Organization(String name, String slug, String timezone, UUID createdBy) {
        this.name = name; this.slug = slug; this.timezone = timezone; this.createdBy = createdBy; this.status = Status.ACTIVE;
    }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void update(String name, String slug, String timezone, Status status) {
        if (name != null) this.name = name;
        if (slug != null) this.slug = slug;
        if (timezone != null) this.timezone = timezone;
        if (status != null) this.status = status;
    }
    public UUID getId() { return id; } public String getName() { return name; } public String getSlug() { return slug; }
    public String getTimezone() { return timezone; } public Status getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
