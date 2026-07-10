package vn.uytinmang.projectos.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectContractTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
    }

    @Autowired MockMvc mvc;

    @Test
    void projectCrudUsesEnvelopeAndRbac() throws Exception {
        UUID actorId = UUID.randomUUID();
        var user = jwt().jwt(token -> token.claim("uid", actorId.toString()).claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        var admin = jwt().jwt(token -> token.claim("uid", actorId.toString()).claim("role", "ROOT_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"));
        String body = "{\"name\":\"PostgreSQL Project\",\"description\":\"Persisted\",\"status\":\"active\",\"icon\":\"P\",\"color\":\"blue\"}";

        mvc.perform(post("/api/v1/projects").with(user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/projects").with(admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("PostgreSQL Project"))
                .andExpect(jsonPath("$.data.ownerId").value(actorId.toString()));

        mvc.perform(get("/api/v1/projects").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.meta.total").value(1));

        mvc.perform(post("/api/v1/projects").with(admin)
                        .cookie(new MockCookie("PROJECT_OS_ACCESS", "cookie-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
