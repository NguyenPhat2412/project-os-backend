package vn.uytinmang.projectos.identity.auth;

import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.identity.user.UserAccount;
import vn.uytinmang.projectos.identity.user.UserAccountRepository;
import vn.uytinmang.projectos.platform.api.ApiException;

@Service
class AuthService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    AuthService(UserAccountRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
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

    @Transactional(readOnly = true)
    Session login(AuthController.LoginRequest request) {
        UserAccount user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> invalidCredentials());
        if (user.getPasswordHash() == null || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return new Session(UserView.from(user), tokens.issue(user));
    }

    @Transactional
    Session google(String emailAddress, String displayName, String avatarUrl) {
        String email = normalize(emailAddress);
        UserAccount user = users.findByEmail(email).orElseGet(() -> users.save(new UserAccount(email, null,
                displayName.trim(), UserAccount.Role.USER)));
        String nextName = displayName == null || displayName.isBlank() ? user.getDisplayName() : displayName.trim();
        String nextAvatar = avatarUrl == null || avatarUrl.isBlank() ? user.getAvatarUrl() : avatarUrl;
        user.updateProfile(nextName, nextAvatar);
        return new Session(UserView.from(user), tokens.issue(user));
    }

    @Transactional(readOnly = true)
    UserView me(UUID id) {
        return users.findById(id).map(UserView::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "user_missing", "User no longer exists"));
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private ApiException invalidCredentials() {
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
