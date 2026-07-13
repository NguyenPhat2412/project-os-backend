package vn.uytinmang.projectos.identity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.uytinmang.projectos.identity.user.UserAccount;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id private UUID id;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RefreshToken() {
    }

    RefreshToken(String tokenHash, UUID familyId, UserAccount user, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.user = user;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UserAccount getUser() { return user; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke() { revokedAt = Instant.now(); }
}
