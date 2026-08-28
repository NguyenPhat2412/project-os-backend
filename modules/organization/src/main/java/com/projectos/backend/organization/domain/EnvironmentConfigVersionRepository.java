package com.projectos.backend.organization.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentConfigVersionRepository extends JpaRepository<EnvironmentConfigVersion, UUID> {
    List<EnvironmentConfigVersion> findTop20ByOrderByCreatedAtDesc();
}
