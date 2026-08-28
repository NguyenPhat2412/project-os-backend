package com.projectos.backend.ai;

import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
    Page<AiMessage> findByConversationIdAndOrganizationIdAndOwnerUserIdOrderByCreatedAtAsc(
            UUID conversationId, UUID organizationId, UUID ownerUserId, Pageable pageable);

    List<AiMessage> findByConversationIdAndOrganizationIdAndOwnerUserIdOrderByCreatedAtAsc(UUID conversationId,
                                                                                              UUID organizationId,
                                                                                              UUID ownerUserId);
}
