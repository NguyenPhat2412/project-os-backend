package com.projectos.backend.identity.user;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    java.util.List<UserAccount> findAllByStatusAndDeleteExpiresAtBefore(UserAccount.Status status, Instant now);
}
