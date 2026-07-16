package vn.uytinmang.projectos.organization.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findBySlug(String slug);
    @Query("select o from Organization o where exists (select 1 from OrganizationMembership m where m.organizationId = o.id and m.userId = :userId and m.status = vn.uytinmang.projectos.organization.domain.OrganizationMembership.Status.ACTIVE)")
    Page<Organization> findAccessible(@Param("userId") UUID userId, Pageable pageable);
}
interface DepartmentRepository extends JpaRepository<Department, UUID> { Page<Department> findByOrganizationId(UUID organizationId, Pageable pageable); }
interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Page<Employee> findByOrganizationId(UUID organizationId, Pageable pageable);
    Page<Employee> findByOrganizationIdAndSupervisorId(UUID organizationId, UUID supervisorId, Pageable pageable);
    Optional<Employee> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    Optional<Employee> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);
    long countByOrganizationIdAndDepartmentId(UUID organizationId, UUID departmentId);
}
interface EmployeeCompensationRepository extends JpaRepository<EmployeeCompensation, UUID> {
    Optional<EmployeeCompensation> findByOrganizationIdAndEmployeeId(UUID organizationId, UUID employeeId);
}
interface CompanyPolicyRepository extends JpaRepository<CompanyPolicy, UUID> {}
interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    Page<OrganizationMembership> findByOrganizationId(UUID organizationId, Pageable pageable);
}
