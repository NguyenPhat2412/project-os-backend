package com.projectos.backend.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_positions")
public class Position {
    public enum Status { ACTIVE, INACTIVE }
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "department_id") private UUID departmentId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String title;
    @Column(name = "job_level") private String jobLevel;
    @Column(name = "standard_salary") private BigDecimal standardSalary;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    private String description;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Position() {}
    public Position(UUID organizationId, UUID departmentId, String code, String title, String jobLevel,
                    BigDecimal standardSalary, String description, UUID actor) {
        this.organizationId = organizationId; this.departmentId = departmentId; this.code = code;
        this.title = title; this.jobLevel = jobLevel; this.standardSalary = standardSalary;
        this.description = description; this.createdBy = actor; this.updatedBy = actor; this.status = Status.ACTIVE;
    }
    public void update(UUID departmentId, String code, String title, String jobLevel, BigDecimal standardSalary,
                       String description, Status status, UUID actor) {
        if (departmentId != null) this.departmentId = departmentId;
        if (code != null) this.code = code;
        if (title != null) this.title = title;
        if (jobLevel != null) this.jobLevel = jobLevel;
        if (standardSalary != null) this.standardSalary = standardSalary;
        if (description != null) this.description = description;
        if (status != null) this.status = status;
        this.updatedBy = actor;
    }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public UUID getId() { return id; } public UUID getOrganizationId() { return organizationId; }
    public UUID getDepartmentId() { return departmentId; } public String getCode() { return code; }
    public String getTitle() { return title; } public String getJobLevel() { return jobLevel; }
    public BigDecimal getStandardSalary() { return standardSalary; } public Status getStatus() { return status; }
    public String getDescription() { return description; } public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; } public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
