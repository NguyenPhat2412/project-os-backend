package vn.uytinmang.projectos.attendance.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="attendance_records")
public class AttendanceRecord {
    public enum Status { OPEN, COMPLETED }
    @Id private UUID id; @Column(name="organization_id",nullable=false) private UUID organizationId; @Column(name="employee_id",nullable=false) private UUID employeeId; @Column(name="shift_id",nullable=false) private UUID shiftId; @Column(name="work_date",nullable=false) private LocalDate workDate; @Column(name="shift_name",nullable=false) private String shiftName; @Column(name="scheduled_start_at",nullable=false) private Instant scheduledStartAt; @Column(name="scheduled_end_at",nullable=false) private Instant scheduledEndAt; @Column(name="check_in_at") private Instant checkInAt; @Column(name="check_out_at") private Instant checkOutAt; @Column(name="break_minutes",nullable=false) private int breakMinutes; @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected AttendanceRecord(){} public AttendanceRecord(UUID organizationId,UUID employeeId,Shift shift,LocalDate workDate,Instant scheduledStartAt,Instant scheduledEndAt,Instant checkInAt){this.organizationId=organizationId;this.employeeId=employeeId;this.shiftId=shift.getId();this.workDate=workDate;this.shiftName=shift.getName();this.scheduledStartAt=scheduledStartAt;this.scheduledEndAt=scheduledEndAt;this.checkInAt=checkInAt;this.breakMinutes=shift.getBreakMinutes();this.status=Status.OPEN;}
    @PrePersist void created(){if(id==null)id=UUID.randomUUID();createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void updated(){updatedAt=Instant.now();}
    public void checkOut(Instant at){this.checkOutAt=at;this.status=Status.COMPLETED;} public void adjust(Instant checkInAt,Instant checkOutAt){if(checkInAt!=null)this.checkInAt=checkInAt;if(checkOutAt!=null){this.checkOutAt=checkOutAt;this.status=Status.COMPLETED;}}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getEmployeeId(){return employeeId;} public UUID getShiftId(){return shiftId;} public LocalDate getWorkDate(){return workDate;} public String getShiftName(){return shiftName;} public Instant getScheduledStartAt(){return scheduledStartAt;} public Instant getScheduledEndAt(){return scheduledEndAt;} public Instant getCheckInAt(){return checkInAt;} public Instant getCheckOutAt(){return checkOutAt;} public int getBreakMinutes(){return breakMinutes;} public Status getStatus(){return status;}
}
