package vn.uytinmang.projectos.identity.auth;

import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.identity.user.UserAccount;
import vn.uytinmang.projectos.identity.user.UserAccountRepository;
import vn.uytinmang.projectos.platform.api.ApiException;

@Service
class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final OAuthIdentityRepository identities;

    AuthService(UserAccountRepository users, PasswordEncoder passwords, TokenService tokens, OAuthIdentityRepository identities) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.identities = identities;
    }

    @Transactional
    Session register(AuthController.RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "email_exists", "Email is already registered");
        }
        UserAccount user = users.save(new UserAccount(email, passwords.encode(request.password()),
                request.displayName().trim(), UserAccount.Role.USER));
        return new Session(UserView.from(user), tokens.issue(user));
    }

    @Transactional
    Session login(AuthController.LoginRequest request) {
        UserAccount user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> invalidCredentials());
        if (user.getStatus() != UserAccount.Status.ACTIVE) throw invalidCredentials();
        if (user.getPasswordHash() == null || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return new Session(UserView.from(user), tokens.issue(user));
    }

    @Transactional
    Session google(String providerSubject, String emailAddress, String displayName, String avatarUrl) {
        String email = normalize(emailAddress);
        UserAccount user = identities.findByProviderAndProviderSubject("GOOGLE", providerSubject)
                .map(OAuthIdentity::getUser)
                .orElseGet(() -> {
                    UserAccount linked = users.findByEmail(email).orElseGet(() -> users.save(new UserAccount(email, null,
                            displayName.trim(), UserAccount.Role.USER)));
                    identities.save(new OAuthIdentity(linked, "GOOGLE", providerSubject));
                    return linked;
                });
        if (user.getStatus() != UserAccount.Status.ACTIVE) throw invalidCredentials();
        String nextName = displayName == null || displayName.isBlank() ? user.getDisplayName() : displayName.trim();
        String nextAvatar = avatarUrl == null || avatarUrl.isBlank() ? user.getAvatarUrl() : avatarUrl;
        user.updateProfile(nextName, nextAvatar);
        return new Session(UserView.from(user), tokens.issue(user));
    }

    @Transactional(readOnly = true)
    UserView me(UUID id) {
        return users.findById(id).filter(user -> user.getStatus() == UserAccount.Status.ACTIVE).map(UserView::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "user_missing", "User no longer exists"));
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private ApiException invalidCredentials() {
        log.warn("auth_login_failed");
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password");
    }

    record Session(UserView user, TokenService.SessionTokens tokens) {
    }

    record UserView(UUID id, String email, String displayName, String avatarUrl, String role) {
        static UserView from(UserAccount user) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
                    user.getRole().name());
        }
    }
}
