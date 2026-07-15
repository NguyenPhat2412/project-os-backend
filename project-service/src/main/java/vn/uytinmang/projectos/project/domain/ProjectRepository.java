package vn.uytinmang.projectos.project.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByLegacyId(String legacyId);
    Page<Project> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    Page<Project> findByOrganizationIdOrderByUpdatedAtDesc(UUID organizationId, Pageable pageable);

    @Query(value = """
            select p.* from project.projects p
            where p.owner_id = :actorId
               or exists (
                    select 1 from project.resource_records r
                    where r.project_id = p.id
                      and r.resource_type in ('members', 'role-assignments')
                      and coalesce(r.payload ->> 'uid', r.payload ->> 'memberId', r.payload ->> 'userId')
                          = cast(:actorId as text)
               )
            order by p.updated_at desc
            """,
            countQuery = """
            select count(*) from project.projects p
            where p.owner_id = :actorId
               or exists (
                    select 1 from project.resource_records r
                    where r.project_id = p.id
                      and r.resource_type in ('members', 'role-assignments')
                      and coalesce(r.payload ->> 'uid', r.payload ->> 'memberId', r.payload ->> 'userId')
                          = cast(:actorId as text)
               )
            """, nativeQuery = true)
    Page<Project> findAccessible(@Param("actorId") UUID actorId, Pageable pageable);

    @Query(value = """
            select p.* from project.projects p
            where p.organization_id = :organizationId
              and (p.owner_id = :actorId
               or exists (
                    select 1 from project.resource_records r
                    where r.project_id = p.id
                      and r.resource_type in ('members', 'role-assignments')
                      and coalesce(r.payload ->> 'uid', r.payload ->> 'memberId', r.payload ->> 'userId')
                          = cast(:actorId as text)
               ))
            order by p.updated_at desc
            """,
            countQuery = """
            select count(*) from project.projects p
            where p.organization_id = :organizationId
              and (p.owner_id = :actorId
               or exists (
                    select 1 from project.resource_records r
                    where r.project_id = p.id
                      and r.resource_type in ('members', 'role-assignments')
                      and coalesce(r.payload ->> 'uid', r.payload ->> 'memberId', r.payload ->> 'userId')
                          = cast(:actorId as text)
               ))
            """, nativeQuery = true)
    Page<Project> findAccessibleByOrganizationId(@Param("actorId") UUID actorId,
                                                  @Param("organizationId") UUID organizationId,
                                                  Pageable pageable);
}
