package com.projectos.backend.resource;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRecordRepository extends JpaRepository<ResourceRecord, UUID> {
    List<ResourceRecord> findAllByProjectIdAndResourceTypeOrderByCreatedAtAsc(UUID projectId, String resourceType);
    Page<ResourceRecord> findAllByProjectIdAndResourceType(UUID projectId, String resourceType, Pageable pageable);
    @org.springframework.data.jpa.repository.Query(value = "select * from resource_records r where r.project_id = :projectId and r.resource_type = :resourceType and (:search = '' or cast(r.payload as text) ilike concat('%', :search, '%')) order by r.created_at asc", countQuery = "select count(*) from resource_records r where r.project_id = :projectId and r.resource_type = :resourceType and (:search = '' or cast(r.payload as text) ilike concat('%', :search, '%'))", nativeQuery = true)
    Page<ResourceRecord> searchByProjectAndResource(UUID projectId, String resourceType, String search, Pageable pageable);
    Optional<ResourceRecord> findByProjectIdAndResourceTypeAndId(UUID projectId, String resourceType, UUID id);
    Optional<ResourceRecord> findByProjectIdAndResourceTypeAndLegacyId(UUID projectId, String resourceType,
                                                                        String legacyId);
}
