package com.projectos.backend.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "company_shared_resources")
public class CompanyResource {
    public enum Status { ACTIVE, INACTIVE }

    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 50) private String category;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, length = 40) private String unit;
    @Column(length = 200) private String location;
    @Column(name = "owner_department_id") private UUID ownerDepartmentId;
    @Column(nullable = false) private boolean bookable;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    private String notes;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CompanyResource() {}
    public CompanyResource(UUID organizationId, String code, String name, String category, int quantity, UUID actor) {
        this.organizationId = organizationId; this.code = code; this.name = name; this.category = category;
        this.quantity = quantity; this.unit = "item"; this.status = Status.ACTIVE; this.createdBy = actor; this.updatedBy = actor;
    }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void update(String code, String name, String category, Integer quantity, String unit, String location,
                       UUID ownerDepartmentId, Boolean bookable, Status status, String notes, UUID actor) {
        if (code != null) this.code = code; if (name != null) this.name = name; if (category != null) this.category = category;
        if (quantity != null) this.quantity = quantity; if (unit != null) this.unit = unit; if (location != null) this.location = location;
        if (ownerDepartmentId != null) this.ownerDepartmentId = ownerDepartmentId; if (bookable != null) this.bookable = bookable;
        if (status != null) this.status = status; if (notes != null) this.notes = notes; this.updatedBy = actor;
    }
    public UUID getId() { return id; } public UUID getOrganizationId() { return organizationId; } public String getCode() { return code; }
    public String getName() { return name; } public String getCategory() { return category; } public int getQuantity() { return quantity; }
    public String getUnit() { return unit; } public String getLocation() { return location; } public UUID getOwnerDepartmentId() { return ownerDepartmentId; }
    public boolean isBookable() { return bookable; } public Status getStatus() { return status; } public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; } public UUID getUpdatedBy() { return updatedBy; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
