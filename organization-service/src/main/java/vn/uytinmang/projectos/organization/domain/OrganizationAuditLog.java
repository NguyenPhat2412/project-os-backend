package vn.uytinmang.projectos.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "organization_audit_logs")
class OrganizationAuditLog {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "entity_type", nullable = false) private String entityType;
    @Column(name = "entity_id") private UUID entityId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_state", columnDefinition = "jsonb") private JsonNode beforeState;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_state", columnDefinition = "jsonb") private JsonNode afterState;
    private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected OrganizationAuditLog() {}
    OrganizationAuditLog(UUID organizationId, UUID actorId, String eventType, String entityType, UUID entityId,
                         JsonNode beforeState, JsonNode afterState, String reason) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.actorId = actorId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getOrganizationId() { return organizationId; }
    UUID getActorId() { return actorId; }
    String getEventType() { return eventType; }
    String getEntityType() { return entityType; }
    UUID getEntityId() { return entityId; }
    JsonNode getBeforeState() { return beforeState; }
    JsonNode getAfterState() { return afterState; }
    String getReason() { return reason; }
    Instant getCreatedAt() { return createdAt; }
}
