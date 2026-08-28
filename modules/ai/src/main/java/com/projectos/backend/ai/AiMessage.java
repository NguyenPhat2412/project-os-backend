package com.projectos.backend.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
class AiMessage {
    enum Role { USER, ASSISTANT }

    @Id private UUID id;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "owner_user_id", nullable = false) private UUID ownerUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(name = "provider_model") private String providerModel;
    @Column(name = "prompt_tokens") private Integer promptTokens;
    @Column(name = "completion_tokens") private Integer completionTokens;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AiMessage() {}

    AiMessage(UUID conversationId, UUID organizationId, UUID ownerUserId, Role role, String content, String providerModel) {
        this.conversationId = conversationId;
        this.organizationId = organizationId;
        this.ownerUserId = ownerUserId;
        this.role = role;
        this.content = content;
        this.providerModel = providerModel;
    }

    @PrePersist
    void created() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getConversationId() { return conversationId; }
    UUID getOrganizationId() { return organizationId; }
    UUID getOwnerUserId() { return ownerUserId; }
    Role getRole() { return role; }
    String getContent() { return content; }
    String getProviderModel() { return providerModel; }
    Integer getPromptTokens() { return promptTokens; }
    Integer getCompletionTokens() { return completionTokens; }
    Instant getCreatedAt() { return createdAt; }
    void setUsage(Integer promptTokens, Integer completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }
}
