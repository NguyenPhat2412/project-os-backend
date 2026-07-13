package vn.uytinmang.projectos.identity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.uytinmang.projectos.identity.user.UserAccount;

@Entity
@Table(name = "oauth_identities")
class OAuthIdentity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(nullable = false) private String provider;
    @Column(name = "provider_subject", nullable = false) private String providerSubject;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected OAuthIdentity() {}
    OAuthIdentity(UserAccount user, String provider, String providerSubject) { this.user = user; this.provider = provider; this.providerSubject = providerSubject; }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); }
    UserAccount getUser() { return user; }
}
