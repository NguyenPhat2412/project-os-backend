package vn.uytinmang.projectos.attendance.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="schedule_assignments")
public class ScheduleAssignment {
    @Id private UUID id; @Column(name="organization_id",nullable=false) private UUID organizationId; @Column(name="employee_id",nullable=false) private UUID employeeId; @Column(name="schedule_id",nullable=false) private UUID scheduleId; @Column(name="effective_from",nullable=false) private LocalDate effectiveFrom; @Column(name="effective_to") private LocalDate effectiveTo; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected ScheduleAssignment(){} public ScheduleAssignment(UUID organizationId,UUID employeeId,UUID scheduleId,LocalDate effectiveFrom,LocalDate effectiveTo){this.organizationId=organizationId;this.employeeId=employeeId;this.scheduleId=scheduleId;this.effectiveFrom=effectiveFrom;this.effectiveTo=effectiveTo;}
    @PrePersist void created(){if(id==null)id=UUID.randomUUID();createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void updated(){updatedAt=Instant.now();} public void update(UUID scheduleId,LocalDate effectiveFrom,LocalDate effectiveTo){if(scheduleId!=null)this.scheduleId=scheduleId;if(effectiveFrom!=null)this.effectiveFrom=effectiveFrom;this.effectiveTo=effectiveTo;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getEmployeeId(){return employeeId;} public UUID getScheduleId(){return scheduleId;} public LocalDate getEffectiveFrom(){return effectiveFrom;} public LocalDate getEffectiveTo(){return effectiveTo;}
}
