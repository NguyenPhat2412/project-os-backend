package vn.uytinmang.projectos.organization;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrganizationContractTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
        registry.add("app.internal-token", () -> "test-internal-token");
    }
    @Autowired MockMvc mvc;

    @Test
    void ownerCanManageDepartmentEmployeeAndMembership() throws Exception {
        UUID ownerId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));
        String organizationId = mvc.perform(post("/api/v1/organizations").with(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Engineering\",\"slug\":\"acme\",\"timezone\":\"Asia/Ho_Chi_Minh\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.slug").value("acme"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        String departmentId = mvc.perform(post("/api/v1/organizations/" + organizationId + "/departments").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Engineering\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mvc.perform(post("/api/v1/organizations/" + organizationId + "/employees").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Jane Doe\",\"email\":\"jane@example.com\",\"departmentId\":\"" + departmentId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("active"));
        UUID memberId = UUID.randomUUID();
        mvc.perform(put("/api/v1/organizations/" + organizationId + "/members").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + memberId + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("member"));
        mvc.perform(get("/api/v1/organizations").with(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));
    }
}
