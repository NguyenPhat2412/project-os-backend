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
@Table(name = "employees")
public class Employee {
    public enum Status { ACTIVE, INACTIVE }
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "department_id") private UUID departmentId;
    @Column(name = "supervisor_id") private UUID supervisorId;
    @Column(name = "user_id") private UUID userId;
    @Column(length = 50) private String code;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(nullable = false) private String email;
    @Column(length = 50) private String phone;
    private String title;
    @Column(length = 4000) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Employee() {}
    public Employee(UUID organizationId, UUID departmentId, UUID supervisorId, String code, String fullName, String email, String phone, String title, String notes) {
        this.organizationId = organizationId; this.departmentId = departmentId; this.supervisorId = supervisorId;
        this.code = code; this.fullName = fullName; this.email = email; this.phone = phone; this.title = title; this.notes = notes; this.status = Status.ACTIVE;
    }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void update(UUID departmentId, UUID supervisorId, String code, String fullName, String email, String phone, String title, String notes, Status status) {
        if (departmentId != null) this.departmentId = departmentId; if (supervisorId != null) this.supervisorId = supervisorId;
        if (code != null) this.code = code; if (fullName != null) this.fullName = fullName; if (email != null) this.email = email;
        if (phone != null) this.phone = phone; if (title != null) this.title = title; if (notes != null) this.notes = notes;
        if (status != null) this.status = status;
    }
    public void linkUser(UUID userId) { this.userId = userId; }
    public UUID getId() { return id; } public UUID getOrganizationId() { return organizationId; } public UUID getDepartmentId() { return departmentId; }
    public UUID getSupervisorId() { return supervisorId; } public UUID getUserId() { return userId; } public String getCode() { return code; }
    public String getFullName() { return fullName; } public String getEmail() { return email; } public String getPhone() { return phone; }
    public String getTitle() { return title; } public String getNotes() { return notes; } public Status getStatus() { return status; }
    public boolean isDeleted() { return deleted; } public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
