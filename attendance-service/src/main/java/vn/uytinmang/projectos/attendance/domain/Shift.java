package vn.uytinmang.projectos.attendance.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "shifts")
public class Shift {
    @Id private UUID id; @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false) private String name; @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime; @Column(name = "break_minutes", nullable = false) private int breakMinutes;
    @Column(nullable = false) private boolean active; @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt; @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Shift() {}
    public Shift(UUID organizationId, String name, LocalTime startTime, LocalTime endTime, int breakMinutes) { this.organizationId=organizationId; this.name=name; this.startTime=startTime; this.endTime=endTime; this.breakMinutes=breakMinutes; this.active=true; }
    @PrePersist void created(){ if(id==null)id=UUID.randomUUID(); createdAt=Instant.now(); updatedAt=createdAt; } @PreUpdate void updated(){updatedAt=Instant.now();}
    public void update(String name, LocalTime startTime, LocalTime endTime, Integer breakMinutes, Boolean active){if(name!=null)this.name=name;if(startTime!=null)this.startTime=startTime;if(endTime!=null)this.endTime=endTime;if(breakMinutes!=null)this.breakMinutes=breakMinutes;if(active!=null)this.active=active;}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public String getName(){return name;} public LocalTime getStartTime(){return startTime;} public LocalTime getEndTime(){return endTime;} public int getBreakMinutes(){return breakMinutes;} public boolean isActive(){return active;}
}
