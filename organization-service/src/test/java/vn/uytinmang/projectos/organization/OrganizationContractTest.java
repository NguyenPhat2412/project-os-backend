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
        String organizationId = organizationFor(ownerId, "Acme Engineering", "acme");

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
    void membershipSynchronizesEmployeeProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));
        String organizationId = organizationFor(ownerId, "Synced Org", "synced-org");

        mvc.perform(put("/api/v1/organizations/" + organizationId + "/members").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + memberId
                                + "\",\"role\":\"MEMBER\",\"fullName\":\"Synced User\",\"email\":\"synced@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/employees").with(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(memberId.toString()))
                .andExpect(jsonPath("$.data[0].fullName").value("Synced User"));

        mvc.perform(put("/api/v1/organizations/" + organizationId + "/members").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + memberId
                                + "\",\"role\":\"MEMBER\",\"fullName\":\"Updated User\",\"email\":\"updated@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/employees").with(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].email").value("updated@example.com"));

        mvc.perform(put("/api/v1/organizations/" + organizationId + "/members").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + UUID.randomUUID()
                                + "\",\"role\":\"MEMBER\",\"fullName\":\"Duplicate User\",\"email\":\"updated@example.com\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("employee_email_already_linked"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/members").with(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(3));
    }

    @Test
    void workspaceAndEmployeeDirectoryRespectOrganizationScope() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));
        String organizationId = organizationFor(ownerId, "Scoped Org", "scoped-org");
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
                "{\"name\":\"Attendance and rules\",\"modules\":[\"attendance\",\"company-rules\",\"meetings\",\"profile\"],\"memberIds\":[\""
                        + employeeId + "\"]}", "id");
        mvc.perform(get("/api/v1/me/workspace?organizationId=" + organizationId).with(manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.modules.length()").value(2))
                .andExpect(jsonPath("$.data.modules[0]").value("dashboard"))
                .andExpect(jsonPath("$.data.modules[1]").value("profile"));
        mvc.perform(get("/api/v1/me/workspace?organizationId=" + organizationId).with(employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.modules.length()").value(4))
                .andExpect(jsonPath("$.data.modules[0]").value("attendance"))
                .andExpect(jsonPath("$.data.modules[1]").value("company-rules"))
                .andExpect(jsonPath("$.data.modules[2]").value("meetings"))
                .andExpect(jsonPath("$.data.modules[3]").value("profile"))
                .andExpect(jsonPath("$.data.permissionGroups[0]").value("Attendance and rules"));
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
        String organizationId = organizationFor(ownerId, "HR Scoped Org", "hr-scoped-org");
        value(put("/api/v1/organizations/" + organizationId + "/members"), owner,
                "{\"userId\":\"" + hrId + "\",\"role\":\"HR\"}", "id");
        var hr = jwt().jwt(token -> token.claim("uid", hrId.toString()).claim("role", "USER"));

        mvc.perform(post("/api/v1/organizations/" + organizationId + "/employees").with(hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"HR Managed Employee\",\"email\":\"managed-by-hr@example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.fullName").value("HR Managed Employee"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/members").with(hr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(3));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/permission-groups").with(hr))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("organization_admin_required"));
    }

    @Test
    void compensationIsWritableByAdminAndVisibleOnlyToTheEmployee() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID employeeUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));
        String organizationId = organizationFor(ownerId, "Compensation Org", "compensation-org");
        String employeeId = value(post("/api/v1/organizations/" + organizationId + "/employees"), owner,
                "{\"fullName\":\"Paid Employee\",\"email\":\"paid@example.com\"}", "id");
        value(post("/api/v1/organizations/" + organizationId + "/employees/" + employeeId + "/link-user"), owner,
                "{\"userId\":\"" + employeeUserId + "\"}", "id");
        String otherEmployeeId = value(post("/api/v1/organizations/" + organizationId + "/employees"), owner,
                "{\"fullName\":\"Other Employee\",\"email\":\"other@example.com\"}", "id");
        value(post("/api/v1/organizations/" + organizationId + "/employees/" + otherEmployeeId + "/link-user"), owner,
                "{\"userId\":\"" + otherUserId + "\"}", "id");

        mvc.perform(put("/api/v1/organizations/" + organizationId + "/employees/" + employeeId + "/compensation")
                        .with(owner).contentType(MediaType.APPLICATION_JSON).content("{\"monthlyAmount\":12000000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.monthlyAmount").value(12000000));

        var employee = jwt().jwt(token -> token.claim("uid", employeeUserId.toString()).claim("role", "USER"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/employees/" + employeeId + "/compensation").with(employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.monthlyAmount").value(12000000));
        mvc.perform(put("/api/v1/organizations/" + organizationId + "/employees/" + employeeId + "/compensation")
                        .with(employee).contentType(MediaType.APPLICATION_JSON).content("{\"monthlyAmount\":1}"))
                .andExpect(status().isForbidden());

        var otherEmployee = jwt().jwt(token -> token.claim("uid", otherUserId.toString()).claim("role", "USER"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/employees/" + employeeId + "/compensation").with(otherEmployee))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("employee_compensation_access_denied"));
    }

    @Test
    void employeeCannotCreateOrganization() throws Exception {
        var employee = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "USER"));
        mvc.perform(post("/api/v1/organizations").with(employee).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized Org\",\"slug\":\"unauthorized-org\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("root_admin_required"));
    }

    @Test
    void companyPolicyIsReadableByEmployeesAndWritableOnlyByOrganizationAdmin() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));
        var employee = jwt().jwt(token -> token.claim("uid", employeeId.toString()).claim("role", "USER"));
        String organizationId = organizationFor(ownerId, "TTA", "tta-rules");
        value(put("/api/v1/organizations/" + organizationId + "/members"), owner,
                "{\"userId\":\"" + employeeId + "\",\"role\":\"MEMBER\"}", "id");

        mvc.perform(get("/api/v1/organizations/" + organizationId + "/company-policy").with(employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.morningStart").value("08:00"))
                .andExpect(jsonPath("$.data.rules.length()").value(4));
        mvc.perform(put("/api/v1/organizations/" + organizationId + "/company-policy").with(employee)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"morningStart\":\"08:00\",\"morningEnd\":\"12:00\",\"afternoonStart\":\"13:00\",\"afternoonEnd\":\"17:00\",\"rules\":[\"Blocked\"]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("organization_admin_required"));
        mvc.perform(put("/api/v1/organizations/" + organizationId + "/company-policy").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"morningStart\":\"08:00\",\"morningEnd\":\"12:00\",\"afternoonStart\":\"13:00\",\"afternoonEnd\":\"17:00\",\"rules\":[\"Focus on assigned work\",\"No personal work during working hours\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rules.length()").value(2));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/company-policy").with(employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rules[1]").value("No personal work during working hours"));
    }

    private String organizationFor(UUID ownerId, String name, String slug) throws Exception {
        var root = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        String organizationId = value(post("/api/v1/organizations"), root,
                "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}", "id");
        value(put("/api/v1/organizations/" + organizationId + "/members"), root,
                "{\"userId\":\"" + ownerId + "\",\"role\":\"OWNER\"}", "id");
        return organizationId;
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
