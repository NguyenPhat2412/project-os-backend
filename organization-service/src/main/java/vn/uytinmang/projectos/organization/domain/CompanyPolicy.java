package vn.uytinmang.projectos.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "company_policies")
public class CompanyPolicy {
    @Id @Column(name = "organization_id") private UUID organizationId;
    @Column(name = "morning_start", nullable = false) private LocalTime morningStart;
    @Column(name = "morning_end", nullable = false) private LocalTime morningEnd;
    @Column(name = "afternoon_start", nullable = false) private LocalTime afternoonStart;
    @Column(name = "afternoon_end", nullable = false) private LocalTime afternoonEnd;
    @Column(nullable = false, columnDefinition = "text") private String rules;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CompanyPolicy() {}
    CompanyPolicy(UUID organizationId, LocalTime morningStart, LocalTime morningEnd, LocalTime afternoonStart,
                  LocalTime afternoonEnd, List<String> rules, UUID updatedBy) {
        this.organizationId = organizationId;
        update(morningStart, morningEnd, afternoonStart, afternoonEnd, rules, updatedBy);
    }
    void update(LocalTime morningStart, LocalTime morningEnd, LocalTime afternoonStart, LocalTime afternoonEnd,
                List<String> rules, UUID updatedBy) {
        this.morningStart = morningStart; this.morningEnd = morningEnd;
        this.afternoonStart = afternoonStart; this.afternoonEnd = afternoonEnd;
        this.rules = String.join("\n", rules); this.updatedBy = updatedBy;
    }
    @PrePersist @PreUpdate void touch() { updatedAt = Instant.now(); }
    public UUID getOrganizationId() { return organizationId; }
    public LocalTime getMorningStart() { return morningStart; }
    public LocalTime getMorningEnd() { return morningEnd; }
    public LocalTime getAfternoonStart() { return afternoonStart; }
    public LocalTime getAfternoonEnd() { return afternoonEnd; }
    public List<String> getRules() { return rules.lines().filter(value -> !value.isBlank()).toList(); }
    public Instant getUpdatedAt() { return updatedAt; }
}
