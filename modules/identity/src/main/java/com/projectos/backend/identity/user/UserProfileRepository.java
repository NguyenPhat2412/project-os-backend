package com.projectos.backend.identity.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
