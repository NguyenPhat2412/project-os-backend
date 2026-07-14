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
        registry.add("app.outbox.enabled", () -> false);
        registry.add("app.rbac.enabled", () -> false);
    }

    @Autowired MockMvc mvc;

    @Test
    void legacyIdCrudReorderAndCsrfUseTheWorkSchema() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var actor = jwt().jwt(token -> token.claim("uid", actorId.toString()).claim("role", "ROOT_ADMIN"));
        String path = "/api/v1/projects/" + projectId + "/tasks";

        mvc.perform(put(path + "/TASK-01").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First task\",\"status\":\"todo\",\"order\":0,\"deadline\":\"2026-07-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("TASK-01"))
                .andExpect(jsonPath("$.data.uuid").isNotEmpty());

        mvc.perform(patch(path + "/TASK-01").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"in_progress\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_progress"));

        mvc.perform(patch(path + "/TASK-01").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deadline\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadline").doesNotExist());

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

    @Test
    void phaseThreeResourcesSupportCrudAndBatchReorder() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var actor = jwt().jwt(token -> token.claim("uid", actorId.toString()).claim("role", "ROOT_ADMIN"));
        String base = "/api/v1/projects/" + projectId + "/";

        mvc.perform(post(base + "task-columns").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legacyId\":\"todo\",\"title\":\"To do\",\"order\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("todo"));
        mvc.perform(post(base + "sprints").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legacyId\":\"SPRINT-01\",\"name\":\"Sprint 1\",\"status\":\"planned\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("SPRINT-01"));
        mvc.perform(post(base + "bugs").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legacyId\":\"BUG-01\",\"title\":\"Broken login\",\"status\":\"open\",\"order\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("BUG-01"));
        mvc.perform(post(base + "bug-columns").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legacyId\":\"open\",\"title\":\"Open\",\"order\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("open"));

        mvc.perform(post(base + "bugs/reorder").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"BUG-01\",\"order\":2,\"status\":\"in-progress\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].order").value(2))
                .andExpect(jsonPath("$.data[0].status").value("in-progress"));

        mvc.perform(get(base + "task-columns").with(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));
        mvc.perform(get(base + "sprints").with(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));
        mvc.perform(get(base + "bug-columns").with(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void employeeOnlyReadsOwnTasksAndCanOnlyPatchStatus() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID colleagueId = UUID.randomUUID();
        var root = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        var employee = jwt().jwt(token -> token.claim("uid", employeeId.toString()).claim("role", "USER"));
        String path = "/api/v1/projects/" + projectId + "/tasks";
        mvc.perform(post(path).with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legacyId\":\"MINE-1\",\"title\":\"Mine\",\"status\":\"todo\",\"assigneeId\":\"" + employeeId + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post(path).with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legacyId\":\"OTHER-1\",\"title\":\"Other\",\"status\":\"todo\",\"assigneeId\":\"" + colleagueId + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/me/tasks?projectId=" + projectId).with(employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value("MINE-1"));
        mvc.perform(get("/api/v1/me/tasks/OTHER-1?projectId=" + projectId).with(employee))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/me/tasks/MINE-1/status?projectId=" + projectId).with(employee)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"done\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("done"));

        String report = "{\"projectId\":\"" + projectId + "\",\"date\":\"2026-07-13\",\"summary\":\"Finished login\"}";
        mvc.perform(post("/api/v1/me/daily-reports").with(employee).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.userId").value(employeeId.toString()));
        mvc.perform(post("/api/v1/me/daily-reports").with(employee).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isConflict());
    }

    @Test
    void rootCanCreateTaskThroughManagerContract() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        var root = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        mvc.perform(post("/api/v1/manager/tasks").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"organizationId\":\"" + UUID.randomUUID()
                                + "\",\"assigneeId\":\"" + assigneeId
                                + "\",\"legacyId\":\"TEAM-1\",\"title\":\"Assigned by manager\",\"status\":\"todo\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("TEAM-1"))
                .andExpect(jsonPath("$.data.assigneeId").value(assigneeId.toString()));
    }
}
