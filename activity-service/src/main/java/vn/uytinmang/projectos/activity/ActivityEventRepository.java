package vn.uytinmang.projectos.activity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    Optional<ActivityEvent> findByEventId(UUID eventId);

    Page<ActivityEvent> findByOrganizationIdAndProjectIdAndActorIdOrderByOccurredAtDesc(
            UUID organizationId, UUID projectId, UUID actorId, Pageable pageable);

    Optional<ActivityEvent> findByIdAndOrganizationIdAndProjectIdAndActorId(
            UUID id, UUID organizationId, UUID projectId, UUID actorId);
}
