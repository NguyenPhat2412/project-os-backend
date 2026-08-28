package com.projectos.backend.ai;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    Page<AiConversation> findByOrganizationIdAndOwnerUserIdOrderByUpdatedAtDesc(UUID organizationId, UUID ownerUserId, Pageable pageable);
    Page<AiConversation> findByOrganizationIdAndOwnerUserIdAndProjectIdOrderByUpdatedAtDesc(UUID organizationId,
                                                                                               UUID ownerUserId,
                                                                                               UUID projectId,
                                                                                               Pageable pageable);
    Page<AiConversation> findByOrganizationIdAndOwnerUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
            UUID organizationId, UUID ownerUserId, String title, Pageable pageable);
    Page<AiConversation> findByOrganizationIdAndOwnerUserIdAndProjectIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
            UUID organizationId, UUID ownerUserId, UUID projectId, String title, Pageable pageable);
    Optional<AiConversation> findByIdAndOrganizationIdAndOwnerUserId(UUID id, UUID organizationId, UUID ownerUserId);
}
