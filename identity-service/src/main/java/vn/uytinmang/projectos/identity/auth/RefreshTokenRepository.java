package vn.uytinmang.projectos.identity.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<RefreshToken> lockByTokenHash(@Param("tokenHash") String tokenHash);
    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :now where token.user.id = :userId and token.revokedAt is null")
    int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now);
    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :now where token.familyId = :familyId and token.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
