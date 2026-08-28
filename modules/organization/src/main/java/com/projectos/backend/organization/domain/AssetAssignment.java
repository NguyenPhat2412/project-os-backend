package com.projectos.backend.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_assignments")
public class AssetAssignment {
    public enum Status { ACTIVE, RETURNED }
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "asset_id", nullable = false) private UUID assetId;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "handover_id", nullable = false) private UUID handoverId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "assigned_at", nullable = false) private Instant assignedAt;
    @Column(name = "returned_at") private Instant returnedAt;
    @Column(name = "assigned_by", nullable = false) private UUID assignedBy;
    @Column(name = "returned_by") private UUID returnedBy;
    private String notes;
    protected AssetAssignment() {}
    public AssetAssignment(UUID organizationId, UUID assetId, UUID employeeId, UUID handoverId, UUID actor, String notes) {
        this.organizationId = organizationId; this.assetId = assetId; this.employeeId = employeeId; this.handoverId = handoverId;
        this.assignedBy = actor; this.notes = notes; this.status = Status.ACTIVE; this.assignedAt = Instant.now();
    }
    public void returned(UUID actor) { status = Status.RETURNED; returnedAt = Instant.now(); returnedBy = actor; }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); }
    public UUID getId() { return id; } public UUID getOrganizationId() { return organizationId; } public UUID getAssetId() { return assetId; }
    public UUID getEmployeeId() { return employeeId; } public UUID getHandoverId() { return handoverId; } public Status getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; } public Instant getReturnedAt() { return returnedAt; } public UUID getAssignedBy() { return assignedBy; }
    public UUID getReturnedBy() { return returnedBy; } public String getNotes() { return notes; }
}
