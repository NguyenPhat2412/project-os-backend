package vn.uytinmang.projectos.resource;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resource_records")
public class ResourceRecord {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "resource_type", nullable = false, length = 80)
    private String resourceType;
    @Column(name = "legacy_id", length = 200)
    private String legacyId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ResourceRecord() {
    }

    ResourceRecord(UUID projectId, String resourceType, String legacyId, JsonNode payload, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.resourceType = resourceType;
        this.legacyId = legacyId;
        this.payload = payload;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void replace(JsonNode payload) {
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getResourceType() { return resourceType; }
    public String getLegacyId() { return legacyId; }
    public JsonNode getPayload() { return payload; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
