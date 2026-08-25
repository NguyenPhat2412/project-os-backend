package com.projectos.backend.monolith.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.projectos.backend.identity.auth.GoogleOAuthSuccessHandler;
import com.projectos.backend.platform.api.ApiSecurityErrorHandler;
import com.projectos.backend.platform.security.CookieBearerTokenResolver;
import com.projectos.backend.platform.security.CookieCsrfFilter;

@Configuration
@EnableMethodSecurity
public class MonolithSecurityConfig {

    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Primary
    public SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    @Primary
    public JwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must contain explicit origins when credentials are enabled");
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Total-Count", "Content-Disposition", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain monolithSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder decoder,
            CorsConfigurationSource corsConfigurationSource,
            ObjectProvider<ClientRegistrationRepository> registrations,
            ObjectProvider<GoogleOAuthSuccessHandler> googleSuccessHandler,
            ApiSecurityErrorHandler errors,
            @Value("${app.security.openapi-public:false}") boolean openapiPublic) throws Exception {

        boolean googleEnabled = registrations.getIfAvailable() != null;
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new CookieCsrfFilter("PROJECT_OS_ACCESS", "PROJECT_OS_REFRESH"),
                        BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        googleEnabled ? SessionCreationPolicy.IF_REQUIRED : SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                            "/api/v1/auth/register",
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout",
                            "/api/v1/auth/providers",
                            "/actuator/health",
                            "/actuator/health/**"
                    ).permitAll();
                    if (openapiPublic) {
                        authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    if (googleEnabled) {
                        authorize.requestMatchers("/api/v1/oauth2/**", "/api/v1/login/oauth2/**").permitAll();
                    }
                    authorize.requestMatchers("/api/v1/internal/**").denyAll();
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(new CookieBearerTokenResolver("PROJECT_OS_ACCESS"))
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));

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
