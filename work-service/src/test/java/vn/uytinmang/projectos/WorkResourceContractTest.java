package vn.uytinmang.projectos;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WorkResourceContractTest {
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
    void legacyIdCrudReorderAndCsrfUseTheWorkSchema() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var actor = jwt().jwt(token -> token.claim("uid", actorId.toString()).claim("role", "ROOT_ADMIN"));
        String path = "/api/v1/projects/" + projectId + "/tasks";

        mvc.perform(put(path + "/TASK-01").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First task\",\"status\":\"todo\",\"order\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("TASK-01"))
                .andExpect(jsonPath("$.data.uuid").isNotEmpty());

        mvc.perform(patch(path + "/TASK-01").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"in_progress\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_progress"));

        mvc.perform(post(path + "/reorder").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"TASK-01\",\"order\":3,\"status\":\"done\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].order").value(3))
                .andExpect(jsonPath("$.data[0].status").value("done"));

        mvc.perform(get(path).with(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("First task"))
                .andExpect(jsonPath("$.meta.total").value(1));

        mvc.perform(patch(path + "/TASK-01").with(actor)
                        .cookie(new MockCookie("PROJECT_OS_ACCESS", "cookie-token"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"todo\"}"))
                .andExpect(status().isForbidden());
    }
}
