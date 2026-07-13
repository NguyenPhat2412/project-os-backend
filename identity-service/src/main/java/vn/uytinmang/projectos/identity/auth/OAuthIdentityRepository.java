package vn.uytinmang.projectos.identity.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, java.util.UUID> {
    Optional<OAuthIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
