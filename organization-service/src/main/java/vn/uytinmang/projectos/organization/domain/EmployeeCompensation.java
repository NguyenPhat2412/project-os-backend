package vn.uytinmang.projectos.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_compensations")
public class EmployeeCompensation {
    @Id @Column(name = "employee_id") private UUID employeeId;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "monthly_amount", nullable = false, precision = 19, scale = 2) private BigDecimal monthlyAmount;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected EmployeeCompensation() {}
    EmployeeCompensation(UUID organizationId, UUID employeeId, BigDecimal monthlyAmount, UUID updatedBy) {
        this.organizationId = organizationId; this.employeeId = employeeId;
        this.monthlyAmount = monthlyAmount; this.updatedBy = updatedBy;
    }
    void update(BigDecimal monthlyAmount, UUID updatedBy) { this.monthlyAmount = monthlyAmount; this.updatedBy = updatedBy; }
    @PrePersist @PreUpdate void updated() { updatedAt = Instant.now(); }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getOrganizationId() { return organizationId; }
    public BigDecimal getMonthlyAmount() { return monthlyAmount; }
    public Instant getUpdatedAt() { return updatedAt; }
}
