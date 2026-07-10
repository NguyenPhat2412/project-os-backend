package vn.uytinmang.projectos.auth;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.common.ApiException;
import vn.uytinmang.projectos.user.UserAccount;
import vn.uytinmang.projectos.user.UserAccountRepository;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    AuthController.AuthResponse register(AuthController.RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(request.password()),
                request.displayName().trim(), UserAccount.Role.USER));
        return response(user);
    }

    @Transactional(readOnly = true)
    AuthController.AuthResponse login(AuthController.LoginRequest request) {
        UserAccount user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return response(user);
    }

    @Transactional(readOnly = true)
    AuthController.UserResponse currentUser(String email) {
        return users.findByEmail(normalize(email)).map(AuthController.UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private AuthController.AuthResponse response(UserAccount user) {
        TokenService.Token token = tokenService.create(user);
        return new AuthController.AuthResponse(token.value(), "Bearer", token.expiresIn(),
                AuthController.UserResponse.from(user));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
