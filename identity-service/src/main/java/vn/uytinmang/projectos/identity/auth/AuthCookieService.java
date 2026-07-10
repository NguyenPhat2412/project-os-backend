package vn.uytinmang.projectos.identity.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
class AuthCookieService {
    static final String ACCESS_COOKIE = "PROJECT_OS_ACCESS";
    static final String REFRESH_COOKIE = "PROJECT_OS_REFRESH";
    private final boolean secure;

    AuthCookieService(@Value("${app.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    void write(HttpServletResponse response, TokenService.SessionTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, tokens.accessToken(), "/",
                tokens.accessMaxAge()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, tokens.refreshToken(),
                "/api/v1/auth", tokens.refreshMaxAge()).toString());
    }

    void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/", 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", "/api/v1/auth", 0).toString());
    }

    private ResponseCookie cookie(String name, String value, String path, long maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Lax")
                .path(path).maxAge(maxAge).build();
    }
}
