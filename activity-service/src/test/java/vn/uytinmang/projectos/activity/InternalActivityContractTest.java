package vn.uytinmang.projectos.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Optional;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(InternalActivityContractTest.ScopeConfiguration.class)
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
    @Autowired ActivityEventRepository events;

    @Test
    void internalEventsRequireTokenAndAreIdempotent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String body = event(eventId, UUID.randomUUID(), UUID.randomUUID(), "tasks", "TASK-01", "created", "Read docs");
        long countBefore = events.count();

        mvc.perform(post("/api/v1/internal/activities").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/internal/activities").header("X-Internal-Token", "test-internal-token")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.eventId").value(eventId))
                    .andExpect(jsonPath("$.data.recorded").value(true));
        }
        assertThat(events.count()).isEqualTo(countBefore + 1);
        assertThat(events.findByEventId(UUID.fromString(eventId))).isPresent();
    }

    @Test
    void publicApiReturnsOnlyCurrentActorsEvents() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID actorOne = UUID.randomUUID();
        UUID actorTwo = UUID.randomUUID();
        UUID firstEvent = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();
        postInternal(event(firstEvent.toString(), projectId, actorOne, "tasks", "TASK-01", "created", "Read docs"));
        postInternal(event(secondEvent.toString(), projectId, actorTwo, "members", actorTwo.toString(), "updated", ""));

        var actorOneToken = root(actorOne);
        mvc.perform(get("/api/v1/projects/" + projectId + "/activities").with(actorOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subject").value("Read docs"))
                .andExpect(jsonPath("$.data[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.data[0].organizationId").doesNotExist());

        UUID actorTwoActivityId = events.findByEventId(secondEvent).orElseThrow().getId();
        mvc.perform(get("/api/v1/projects/" + projectId + "/activities/" + actorTwoActivityId).with(actorOneToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicApiCannotForgeAppendOnlyActivities() throws Exception {
        var root = root(UUID.randomUUID());

        mvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/activities").with(root)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"forged\"}"))
                        .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("resource_immutable"));
    }

    private void postInternal(String body) throws Exception {
        mvc.perform(post("/api/v1/internal/activities").header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
    }

    private static RequestPostProcessor root(UUID actorId) {
        return jwt().jwt(token -> token.claim("uid", actorId.toString()).claim("role", "ROOT_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"));
    }

    private static String event(String eventId, UUID projectId, UUID actorId, String resource, String resourceId,
                                String action, String title) {
        return "{\"eventId\":\"" + eventId + "\",\"projectId\":\"" + projectId + "\",\"actorId\":\""
                + actorId + "\",\"resource\":\"" + resource + "\",\"resourceId\":\"" + resourceId
                + "\",\"action\":\"" + action + "\",\"occurredAt\":\"2026-07-15T07:00:00Z\",\"snapshot\":{\"title\":\""
                + title + "\"}}";
    }

    @TestConfiguration
    static class ScopeConfiguration {
        @Bean
        ProjectScopeResolver projectScopeResolver() {
            UUID organizationId = UUID.fromString("11111111-1111-4111-8111-111111111111");
            return projectId -> Optional.of(organizationId);
        }
    }
}
