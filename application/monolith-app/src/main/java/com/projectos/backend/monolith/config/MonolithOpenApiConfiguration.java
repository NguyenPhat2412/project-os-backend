package com.projectos.backend.monolith.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single OpenAPI definition for the modular monolith.
 *
 * <p>Springdoc discovers controllers from the domain modules on the monolith
 * classpath. This bean provides the shared contract metadata and documents the
 * cookie-based authentication used by the web application.</p>
 */
@Configuration
public class MonolithOpenApiConfiguration {

    @Bean
    public OpenAPI projectOsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Project OS API")
                        .version("v1")
                        .description("Unified REST API for the Project OS modular monolith.")
                        .contact(new Contact().name("Project OS Engineering")))
                .components(new Components()
                        .addSecuritySchemes("accessCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("PROJECT_OS_ACCESS")
                                .description("HttpOnly access-token cookie issued by the authentication API."))
                        .addSecuritySchemes("csrfToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("CSRF header required for browser mutation requests.")))
                .addSecurityItem(new SecurityRequirement().addList("accessCookie"));
    }
}
