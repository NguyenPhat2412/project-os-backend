package vn.uytinmang.projectos.identity.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService auth;
    private final AuthCookieService cookies;
    private final String successUrl;

    GoogleOAuthSuccessHandler(AuthService auth, AuthCookieService cookies,
                              @Value("${app.google.success-url:http://localhost:3000/projects}") String successUrl) {
        this.auth = auth;
        this.cookies = cookies;
        this.successUrl = successUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new ServletException("Unsupported OAuth authentication");
        }
        OAuth2User principal = token.getPrincipal();
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        String picture = principal.getAttribute("picture");
        String subject = principal.getAttribute("sub");
        if (email == null || email.isBlank()) throw new ServletException("Google account did not provide an email");
        if (subject == null || subject.isBlank()) throw new ServletException("Google account did not provide a subject");
        AuthService.Session session = auth.google(subject, email, name == null ? email : name, picture);
        cookies.write(response, session.tokens());
        response.sendRedirect(successUrl);
    }
}
