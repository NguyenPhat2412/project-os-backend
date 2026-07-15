package vn.uytinmang.projectos.gateway;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReadModelContractTest {
    private static HttpServer downstream;
    private static int port;

    static {
        try {
            downstream = HttpServer.create(new InetSocketAddress(0), 0);
            port = downstream.getAddress().getPort();
            downstream.createContext("/api/v1/projects", ReadModelContractTest::respond);
            downstream.createContext("/api/v1/users/directory", ReadModelContractTest::respond);
            downstream.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) downstream.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("PROJECT_SERVICE_URL", () -> "http://127.0.0.1:" + port);
        registry.add("IDENTITY_SERVICE_URL", () -> "http://127.0.0.1:" + port);
        registry.add("WORK_SERVICE_URL", () -> "http://127.0.0.1:" + port);
        registry.add("OPERATIONS_SERVICE_URL", () -> "http://127.0.0.1:" + port);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
        registry.add("spring.data.redis.connect-timeout", () -> "50ms");
        registry.add("spring.data.redis.timeout", () -> "50ms");
    }

    @Autowired MockMvc mvc;

    @Test
    void dashboardWorkloadAndReportsUseOneStableEnvelope() throws Exception {
        UUID projectId = UUID.randomUUID();
        var user = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        String base = "/api/v1/projects/" + projectId + "/read-model";

        mvc.perform(get(base + "/dashboard").with(user))
                .andExpect(status().isOk())
                .andExpect(header().exists(ReadModelCache.HEADER))
                .andExpect(jsonPath("$.data.summary.tasks").value(1))
                .andExpect(jsonPath("$.data.summary.members").value(1))
                .andExpect(jsonPath("$.data.team[0].name").value("User One"))
                .andExpect(jsonPath("$.data.team[0].roles[0]").value("Developer"));
        mvc.perform(get(base + "/workload").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workload[0].tasks").value(1));
        mvc.perform(get(base + "/reports/tasks").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("TASK-01"))
                .andExpect(jsonPath("$.data.members[0].memberId").value("user-1"));
        mvc.perform(get(base + "/reports/tasks/export.csv").with(user))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=tasks-report.csv"))
                .andExpect(header().exists(ReadModelCache.HEADER));
        mvc.perform(get(base + "/reports/unknown").with(user)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/" + projectId + "/not-a-resource").with(user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    private static void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String data;
        if (path.endsWith("/users/directory")) {
            data = "[{\"id\":\"user-1\",\"displayName\":\"User One\",\"email\":\"user.one@example.com\"}]";
        } else if (path.endsWith("/tasks")) {
            data = "[{\"id\":\"TASK-01\",\"assigneeId\":\"user-1\",\"points\":3,\"status\":\"todo\"}]";
        } else if (path.endsWith("/members")) {
            data = "[{\"id\":\"member-1\",\"memberId\":\"user-1\",\"roles\":[\"Developer\"]}]";
        } else {
            data = "[]";
        }
        byte[] body = ("{\"data\":" + data + ",\"meta\":{\"page\":0,\"size\":200,\"total\":1}}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
