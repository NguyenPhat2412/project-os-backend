package com.projectos.backend.organization.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organization_settings")
public class OrganizationSettings {
    @Id @Column(name = "organization_id") private UUID organizationId;
    /**
     * Keep the JSONB value as its canonical JSON text in the entity. Hibernate
     * uses Jackson 2 for JSON deep-copy operations, while the application runs
     * on Spring's Jackson 3 API; mapping the field directly to a Jackson 3
     * JsonNode makes repository.save() fail during merge.
     */
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String settings;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected OrganizationSettings() {}
    OrganizationSettings(UUID organizationId, JsonNode settings, UUID updatedBy) { this.organizationId = organizationId; this.settings = settings.toString(); this.updatedBy = updatedBy; this.updatedAt = Instant.now(); }
    void replace(JsonNode next, UUID actor) { this.settings = next.toString(); this.updatedBy = actor; this.updatedAt = Instant.now(); }
    public JsonNode getSettings() {
        if (settings == null || settings.isBlank()) return null;
        try {
            return new ObjectMapper().readTree(settings);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
