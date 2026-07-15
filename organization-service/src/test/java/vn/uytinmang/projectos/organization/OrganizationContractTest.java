package vn.uytinmang.projectos.organization;

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
        registry.add("app.workspace-cache.enabled", () -> "false");
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

    @Test
    void workspaceAndEmployeeDirectoryRespectOrganizationScope() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));
        String organizationId = value(post("/api/v1/organizations"), owner,
                "{\"name\":\"Scoped Org\",\"slug\":\"scoped-org\"}", "id");
        String managerEmployeeId = value(post("/api/v1/organizations/" + organizationId + "/employees"), owner,
                "{\"fullName\":\"Team Manager\",\"email\":\"manager@example.com\"}", "id");
        value(post("/api/v1/organizations/" + organizationId + "/employees/" + managerEmployeeId + "/link-user"),
                owner, "{\"userId\":\"" + managerId + "\"}", "id");
        value(put("/api/v1/organizations/" + organizationId + "/members"), owner,
                "{\"userId\":\"" + managerId + "\",\"role\":\"DEPARTMENT_MANAGER\"}", "id");
        String employeeRecordId = value(post("/api/v1/organizations/" + organizationId + "/employees"), owner,
                "{\"fullName\":\"Team Employee\",\"email\":\"employee@example.com\",\"supervisorId\":\""
                        + managerEmployeeId + "\"}", "id");
        value(post("/api/v1/organizations/" + organizationId + "/employees/" + employeeRecordId + "/link-user"),
                owner, "{\"userId\":\"" + employeeId + "\"}", "id");

        var employee = jwt().jwt(token -> token.claim("uid", employeeId.toString()).claim("role", "USER"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/employees").with(employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(employeeId.toString()));
        mvc.perform(get("/api/v1/me/workspace?organizationId=" + organizationId).with(employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.systemRole").value("EMPLOYEE"))
                .andExpect(jsonPath("$.data.scopes.tasks").value("SELF"))
                .andExpect(jsonPath("$.data.modules").isArray());

        var manager = jwt().jwt(token -> token.claim("uid", managerId.toString()).claim("role", "USER"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/employees").with(manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(employeeId.toString()));

        String groupId = value(post("/api/v1/organizations/" + organizationId + "/permission-groups"), owner,
                "{\"name\":\"Attendance only\",\"modules\":[\"attendance\",\"profile\"],\"memberIds\":[\""
                        + employeeId + "\"]}", "id");
        mvc.perform(get("/api/v1/me/workspace?organizationId=" + organizationId).with(employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.modules.length()").value(2))
                .andExpect(jsonPath("$.data.modules[0]").value("attendance"))
                .andExpect(jsonPath("$.data.modules[1]").value("profile"))
                .andExpect(jsonPath("$.data.permissionGroups[0]").value("Attendance only"));
        mvc.perform(patch("/api/v1/organizations/" + organizationId + "/permission-groups/" + groupId)
                        .with(owner).contentType(MediaType.APPLICATION_JSON).content("{\"memberIds\":[\""
                                + employeeId + "\",\"" + managerId + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.memberIds.length()").value(2));
        mvc.perform(patch("/api/v1/organizations/" + organizationId + "/permission-groups/" + groupId)
                        .with(owner).contentType(MediaType.APPLICATION_JSON).content("{\"modules\":[\"tasks\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.modules[0]").value("tasks"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/permission-groups").with(employee))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/audit").with(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data[0].eventType").value("permission_group_updated"));
    }

    @Test
    void hrCanManageEmployeeProfilesButCannotManagePermissionGroups() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID hrId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));
        String organizationId = value(post("/api/v1/organizations"), owner,
                "{\"name\":\"HR Scoped Org\",\"slug\":\"hr-scoped-org\"}", "id");
        value(put("/api/v1/organizations/" + organizationId + "/members"), owner,
                "{\"userId\":\"" + hrId + "\",\"role\":\"HR\"}", "id");
        var hr = jwt().jwt(token -> token.claim("uid", hrId.toString()).claim("role", "USER"));

        mvc.perform(post("/api/v1/organizations/" + organizationId + "/employees").with(hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"HR Managed Employee\",\"email\":\"managed-by-hr@example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.fullName").value("HR Managed Employee"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/members").with(hr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(2));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/permission-groups").with(hr))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("organization_admin_required"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor,
            String body) {
        return builder.with(actor).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String value(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                         org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor,
                         String body, String field) throws Exception {
        String json = mvc.perform(request(builder, actor, body)).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\\\"" + field + "\\\":\\\"([^\\\"]+).*", "$1");
    }
}
