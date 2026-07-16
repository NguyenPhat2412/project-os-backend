package vn.uytinmang.projectos.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_events")
class ActivityEvent {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false, unique = true) private UUID eventId;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(nullable = false) private String resource;
    @Column(nullable = false) private String action;
    @Column(nullable = false) private String subject;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ActivityEvent() {
    }

    ActivityEvent(UUID eventId, UUID organizationId, UUID projectId, UUID actorId, String resource,
                  String action, String subject, Instant occurredAt) {
        this.eventId = eventId;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.resource = resource;
        this.action = action;
        this.subject = subject;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    void created() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getEventId() { return eventId; }
    UUID getOrganizationId() { return organizationId; }
    UUID getProjectId() { return projectId; }
    UUID getActorId() { return actorId; }
    String getResource() { return resource; }
    String getAction() { return action; }
    String getSubject() { return subject; }
    Instant getOccurredAt() { return occurredAt; }
}
