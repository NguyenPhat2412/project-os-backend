package vn.uytinmang.projectos.identity.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final TokenService tokens;
    private final AuthCookieService cookies;

    public AuthController(AuthService auth, TokenService tokens, AuthCookieService cookies) {
        this.auth = auth;
        this.tokens = tokens;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AuthView> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return session(auth.register(request), response);
    }

    @PostMapping("/login")
    ApiResponse<AuthView> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return session(auth.login(request), response);
    }

    @PostMapping("/refresh")
    ApiResponse<AuthView> refresh(HttpServletRequest request, HttpServletResponse response) {
        TokenService.RotatedSession rotated = tokens.rotate(cookie(request, AuthCookieService.REFRESH_COOKIE));
        cookies.write(response, rotated.tokens());
        return ApiResponse.of(new AuthView(AuthService.UserView.from(rotated.user()),
                rotated.tokens().accessMaxAge()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        tokens.revoke(cookie(request, AuthCookieService.REFRESH_COOKIE));
        cookies.clear(response);
    }

    @GetMapping("/me")
    ApiResponse<AuthService.UserView> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(auth.me(UUID.fromString(jwt.getClaimAsString("uid"))));
    }

    private ApiResponse<AuthView> session(AuthService.Session session, HttpServletResponse response) {
        cookies.write(response, session.tokens());
        return ApiResponse.of(new AuthView(session.user(), session.tokens().accessMaxAge()));
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return "";
        return Arrays.stream(request.getCookies()).filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse("");
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 100) String displayName) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthView(AuthService.UserView user, long expiresIn) {
    }
}
