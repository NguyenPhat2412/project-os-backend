package vn.uytinmang.projectos.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.uytinmang.projectos.resource.ResourceRecordRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InternalActivityContractTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
        registry.add("app.outbox.internal-token", () -> "test-internal-token");
    }

    @Autowired MockMvc mvc;
    @Autowired ResourceRecordRepository records;

    @Test
    void internalEventsRequireTokenAndAreIdempotent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String body = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"tasks.created\","
                + "\"projectId\":\"" + UUID.randomUUID() + "\",\"actorId\":\"" + UUID.randomUUID()
                + "\",\"resource\":\"tasks\",\"resourceId\":\"TASK-01\",\"action\":\"created\"}";

        mvc.perform(post("/api/v1/internal/activities").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/internal/activities").header("X-Internal-Token", "test-internal-token")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.legacyId").value(eventId));
        }
        assertThat(records.count()).isEqualTo(1);
    }

    @Test
    void publicApiCannotForgeAppendOnlyActivities() throws Exception {
        var root = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString())
                        .claim("role", "ROOT_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"));

        mvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/activities").with(root)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"forged\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("resource_immutable"));
    }
}
