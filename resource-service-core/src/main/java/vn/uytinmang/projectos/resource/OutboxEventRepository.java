package vn.uytinmang.projectos.resource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant now);
}
