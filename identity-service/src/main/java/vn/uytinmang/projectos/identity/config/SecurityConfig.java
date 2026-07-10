package vn.uytinmang.projectos.identity.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import vn.uytinmang.projectos.platform.security.CookieBearerTokenResolver;
import vn.uytinmang.projectos.platform.security.CookieCsrfFilter;
import vn.uytinmang.projectos.identity.auth.GoogleOAuthSuccessHandler;

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean SecurityFilterChain security(HttpSecurity http, JwtDecoder decoder,
                                       ObjectProvider<ClientRegistrationRepository> registrations,
                                       ObjectProvider<GoogleOAuthSuccessHandler> googleSuccessHandler) throws Exception {
        boolean googleEnabled = registrations.getIfAvailable() != null;
        http.csrf(csrf -> csrf.disable())
                .addFilterBefore(new CookieCsrfFilter("PROJECT_OS_ACCESS", "PROJECT_OS_REFRESH"),
                        BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        googleEnabled ? SessionCreationPolicy.IF_REQUIRED : SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                            "/api/v1/auth/logout", "/actuator/health").permitAll();
                    if (googleEnabled) {
                        authorize.requestMatchers("/api/v1/oauth2/**", "/api/v1/login/oauth2/**").permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(new CookieBearerTokenResolver("PROJECT_OS_ACCESS"))
                        .jwt(jwt -> jwt.decoder(decoder)));
        if (googleEnabled) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/v1/oauth2/authorization"))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/v1/login/oauth2/code/*"))
                    .successHandler(googleSuccessHandler.getObject())
                    .failureUrl("/login?oauthError=1"));
        }
        return http.build();
    }
}
