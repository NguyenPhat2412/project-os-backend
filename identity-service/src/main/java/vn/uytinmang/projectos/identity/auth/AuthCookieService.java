package vn.uytinmang.projectos.identity.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import vn.uytinmang.projectos.platform.security.CookieCsrfFilter;

@Service
public class AuthCookieService {
    static final String ACCESS_COOKIE = "PROJECT_OS_ACCESS";
    static final String REFRESH_COOKIE = "PROJECT_OS_REFRESH";
    private static final String ROOT_PATH = "/";
    private static final String LEGACY_REFRESH_PATH = "/api/v1/auth";
    private final boolean secure;
    private final SecureRandom random = new SecureRandom();

    AuthCookieService(@Value("${app.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    void write(HttpServletResponse response, TokenService.SessionTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, tokens.accessToken(), ROOT_PATH,
                tokens.accessMaxAge()).toString());
        // Refresh must reach the Next.js route middleware on protected page requests.
        // Expire the former scoped cookie first to avoid two cookies with the same name.
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", LEGACY_REFRESH_PATH, 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, tokens.refreshToken(), ROOT_PATH,
                tokens.refreshMaxAge()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(csrfToken(), tokens.refreshMaxAge()).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", ROOT_PATH, 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", ROOT_PATH, 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", LEGACY_REFRESH_PATH, 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie("", 0).toString());
    }

    private ResponseCookie cookie(String name, String value, String path, long maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Lax")
                .path(path).maxAge(maxAge).build();
    }

    private ResponseCookie csrfCookie(String value, long maxAge) {
        return ResponseCookie.from(CookieCsrfFilter.CSRF_COOKIE, value).httpOnly(false).secure(secure)
                .sameSite("Lax").path("/").maxAge(maxAge).build();
    }

    private String csrfToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
