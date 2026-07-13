package vn.uytinmang.projectos.attendance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "work_schedules")
public class WorkSchedule {
    @Id private UUID id; @Column(name="organization_id",nullable=false) private UUID organizationId; @Column(nullable=false) private String name; @Column(nullable=false) private boolean active; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected WorkSchedule(){} public WorkSchedule(UUID organizationId,String name){this.organizationId=organizationId;this.name=name;this.active=true;}
    @PrePersist void created(){if(id==null)id=UUID.randomUUID();createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void updated(){updatedAt=Instant.now();}
    public void update(String name,Boolean active){if(name!=null)this.name=name;if(active!=null)this.active=active;} public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public String getName(){return name;} public boolean isActive(){return active;}
}
