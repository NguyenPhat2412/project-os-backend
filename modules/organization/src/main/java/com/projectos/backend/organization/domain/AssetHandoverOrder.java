package com.projectos.backend.organization.domain;

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
@Table(name = "asset_handover_orders")
public class AssetHandoverOrder {
    public enum Status { PENDING, CONFIRMED, RETURNED, CANCELLED }
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, length = 500) private String purpose;
    private String notes;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "returned_at") private Instant returnedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AssetHandoverOrder() {}
    public AssetHandoverOrder(UUID organizationId, UUID employeeId, String purpose, String notes, UUID actor) {
        this.organizationId = organizationId; this.employeeId = employeeId; this.purpose = purpose; this.notes = notes;
        this.createdBy = actor; this.status = Status.PENDING;
    }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void confirm() { status = Status.CONFIRMED; confirmedAt = Instant.now(); }
    public void returned() { status = Status.RETURNED; returnedAt = Instant.now(); }
    public UUID getId() { return id; } public UUID getOrganizationId() { return organizationId; } public UUID getEmployeeId() { return employeeId; }
    public Status getStatus() { return status; } public String getPurpose() { return purpose; } public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; } public Instant getConfirmedAt() { return confirmedAt; } public Instant getReturnedAt() { return returnedAt; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
