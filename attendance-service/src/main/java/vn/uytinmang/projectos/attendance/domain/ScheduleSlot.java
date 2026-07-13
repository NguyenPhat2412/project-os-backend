package vn.uytinmang.projectos.attendance.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="schedule_slots")
public class ScheduleSlot {
    @Id private UUID id; @Column(name="schedule_id",nullable=false) private UUID scheduleId; @Column(name="shift_id",nullable=false) private UUID shiftId; @Column(name="day_of_week",nullable=false) private short dayOfWeek;
    protected ScheduleSlot(){} public ScheduleSlot(UUID scheduleId,UUID shiftId,int dayOfWeek){this.scheduleId=scheduleId;this.shiftId=shiftId;this.dayOfWeek=(short)dayOfWeek;} @PrePersist void created(){if(id==null)id=UUID.randomUUID();}
    public UUID getId(){return id;} public UUID getScheduleId(){return scheduleId;} public UUID getShiftId(){return shiftId;} public int getDayOfWeek(){return dayOfWeek;}
}
