package vn.uytinmang.projectos.organization.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionGroupRepository extends JpaRepository<PermissionGroup, UUID> {
    List<PermissionGroup> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<PermissionGroup> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationId(UUID organizationId);
    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);
}

interface PermissionGroupMemberRepository extends JpaRepository<PermissionGroupMember, UUID> {
    List<PermissionGroupMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    List<PermissionGroupMember> findByGroupId(UUID groupId);
    void deleteByGroupId(UUID groupId);
}

interface OrganizationAuditLogRepository extends JpaRepository<OrganizationAuditLog, UUID> {
    Page<OrganizationAuditLog> findByOrganizationId(UUID organizationId, Pageable pageable);
}
