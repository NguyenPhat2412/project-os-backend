package vn.uytinmang.projectos.attendance.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="attendance_adjustments")
public class AttendanceAdjustment {
    public enum Status { PENDING, APPROVED, REJECTED }
    @Id private UUID id; @Column(name="organization_id",nullable=false) private UUID organizationId; @Column(name="employee_id",nullable=false) private UUID employeeId; @Column(name="work_date",nullable=false) private LocalDate workDate; @Column(name="requested_check_in_at") private Instant requestedCheckInAt; @Column(name="requested_check_out_at") private Instant requestedCheckOutAt; @Column(nullable=false) private String reason; @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status; @Column(name="reviewer_id") private UUID reviewerId; @Column(name="reviewed_at") private Instant reviewedAt; @Column(name="decision_note") private String decisionNote; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected AttendanceAdjustment(){} public AttendanceAdjustment(UUID organizationId,UUID employeeId,LocalDate workDate,Instant checkIn,Instant checkOut,String reason){this.organizationId=organizationId;this.employeeId=employeeId;this.workDate=workDate;this.requestedCheckInAt=checkIn;this.requestedCheckOutAt=checkOut;this.reason=reason;this.status=Status.PENDING;}
    @PrePersist void created(){if(id==null)id=UUID.randomUUID();createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void updated(){updatedAt=Instant.now();} public void decide(Status status,UUID reviewerId,String note){this.status=status;this.reviewerId=reviewerId;this.reviewedAt=Instant.now();this.decisionNote=note;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getEmployeeId(){return employeeId;} public LocalDate getWorkDate(){return workDate;} public Instant getRequestedCheckInAt(){return requestedCheckInAt;} public Instant getRequestedCheckOutAt(){return requestedCheckOutAt;} public String getReason(){return reason;} public Status getStatus(){return status;} public UUID getReviewerId(){return reviewerId;} public Instant getReviewedAt(){return reviewedAt;} public String getDecisionNote(){return decisionNote;}
}
