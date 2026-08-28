package com.projectos.backend.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversations")
class AiConversation {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "project_id") private UUID projectId;
    @Column(name = "owner_user_id", nullable = false) private UUID ownerUserId;
    @Column(nullable = false) private String title;
    @Column(name = "model_id", nullable = false) private String modelId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AiConversation() {}

    AiConversation(UUID organizationId, UUID projectId, UUID ownerUserId, String title, String modelId) {
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.modelId = modelId;
    }

    @PrePersist
    void timestamps() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getOrganizationId() { return organizationId; }
    UUID getProjectId() { return projectId; }
    UUID getOwnerUserId() { return ownerUserId; }
    String getTitle() { return title; }
    String getModelId() { return modelId; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
