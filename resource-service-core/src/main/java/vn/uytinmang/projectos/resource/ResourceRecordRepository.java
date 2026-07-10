package vn.uytinmang.projectos.resource;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRecordRepository extends JpaRepository<ResourceRecord, UUID> {
    Page<ResourceRecord> findAllByProjectIdAndResourceType(UUID projectId, String resourceType, Pageable pageable);
    Optional<ResourceRecord> findByProjectIdAndResourceTypeAndId(UUID projectId, String resourceType, UUID id);
    Optional<ResourceRecord> findByProjectIdAndResourceTypeAndLegacyId(UUID projectId, String resourceType,
                                                                        String legacyId);
}
