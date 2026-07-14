package vn.uytinmang.projectos.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import vn.uytinmang.projectos.project.domain.ProjectRepository;

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
        registry.add("app.outbox.enabled", () -> false);
        registry.add("app.rbac.enabled", () -> false);
        registry.add("app.rbac.internal-token", () -> "test-internal-token");
    }

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;

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

        UUID projectId = projects.findAll().getFirst().getId();
        mvc.perform(put("/api/v1/projects/" + projectId + "/settings/dashboard").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"layout\":\"compact\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.layout").value("compact"));
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + actorId).with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + actorId + "\",\"roles\":[\"Developer\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roles[0]").value("Developer"));
        mvc.perform(put("/api/v1/projects/" + projectId + "/roles/developer").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Developer\",\"permissions\":[\"tasks:read\",\"tasks:update\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Developer"));

        mvc.perform(get("/api/v1/projects").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.meta.total").isNumber());

        var outsider = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString())
                        .claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        mvc.perform(get("/api/v1/projects").with(outsider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.total").value(0));

        mvc.perform(post("/api/v1/projects").with(admin)
                        .cookie(new MockCookie("PROJECT_OS_ACCESS", "cookie-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void genericTaskReadRequiresManagerOrExplicitPermission() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID developerId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "ROOT_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"));
        mvc.perform(post("/api/v1/projects").with(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Scoped Tasks\",\"status\":\"active\"}"))
                .andExpect(status().isCreated());
        UUID projectId = projects.findAll().stream().filter(project -> "Scoped Tasks".equals(project.getName()))
                .findFirst().orElseThrow().getId();
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + developerId).with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + developerId + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/" + projectId + "/role-assignments/" + developerId).with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + developerId + "\",\"roles\":[\"developer\"]}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/internal/projects/" + projectId + "/permissions/check")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("actorId", developerId.toString()).param("resource", "tasks").param("action", "read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false));
        mvc.perform(get("/api/v1/internal/projects/" + projectId + "/permissions/check")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("actorId", developerId.toString()).param("resource", "tasks-all").param("action", "read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false));
        mvc.perform(get("/api/v1/internal/projects/" + projectId + "/permissions/check")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("actorId", developerId.toString()).param("resource", "projects").param("action", "read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(true));
    }
}
