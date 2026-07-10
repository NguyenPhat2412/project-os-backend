package vn.uytinmang.projectos.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "projects")
public class Project {
    public enum Status { ACTIVE, COMPLETED, ARCHIVED }

    @Id private UUID id;
    @Column(name = "legacy_id", unique = true) private String legacyId;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private String icon;
    @Column(nullable = false) private String color;
    @Column(name = "current_sprint") private String currentSprint;
    private String quarter;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "tech_stack", nullable = false, columnDefinition = "jsonb")
    private List<String> techStack = new ArrayList<>();
    @Column(name = "team_size") private Integer teamSize;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Project() {
    }

    public Project(String name, String description, Status status, String icon, String color,
                   String currentSprint, String quarter, LocalDate startDate, LocalDate endDate,
                   List<String> techStack, Integer teamSize, UUID ownerId) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.icon = icon;
        this.color = color;
        this.currentSprint = currentSprint;
        this.quarter = quarter;
        this.startDate = startDate;
        this.endDate = endDate;
        this.techStack = techStack == null ? new ArrayList<>() : new ArrayList<>(techStack);
        this.teamSize = teamSize;
        this.ownerId = ownerId;
    }

    @PrePersist void create() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public void update(String name, String description, Status status, String icon, String color,
                       String currentSprint, String quarter, LocalDate startDate, LocalDate endDate,
                       List<String> techStack, Integer teamSize) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (status != null) this.status = status;
        if (icon != null) this.icon = icon;
        if (color != null) this.color = color;
        if (currentSprint != null) this.currentSprint = currentSprint;
        if (quarter != null) this.quarter = quarter;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (techStack != null) this.techStack = new ArrayList<>(techStack);
        if (teamSize != null) this.teamSize = teamSize;
    }

    public UUID getId() { return id; }
    public String getLegacyId() { return legacyId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public String getCurrentSprint() { return currentSprint; }
    public String getQuarter() { return quarter; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public List<String> getTechStack() { return List.copyOf(techStack); }
    public Integer getTeamSize() { return teamSize; }
    public UUID getOwnerId() { return ownerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
