package vn.uytinmang.projectos.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Double-submit CSRF protection for browser requests authenticated by cookies.
 * Bearer-token clients are not subject to this check.
 */
public final class CookieCsrfFilter extends OncePerRequestFilter {
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());

    private final Set<String> authenticationCookies;

    public CookieCsrfFilter(String... authenticationCookies) {
        this.authenticationCookies = Set.copyOf(Arrays.asList(authenticationCookies));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || hasBearerHeader(request)
                || !hasAuthenticationCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String cookieValue = cookie(request, CSRF_COOKIE);
        String headerValue = request.getHeader(CSRF_HEADER);
        if (cookieValue == null || headerValue == null || !constantTimeEquals(cookieValue, headerValue)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing or invalid CSRF token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasBearerHeader(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    private boolean hasAuthenticationCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return false;
        return Arrays.stream(request.getCookies()).anyMatch(cookie -> authenticationCookies.contains(cookie.getName()));
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
