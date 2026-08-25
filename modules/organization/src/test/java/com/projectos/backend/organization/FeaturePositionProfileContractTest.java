package com.projectos.backend.organization;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class FeaturePositionProfileContractTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
        registry.add("app.internal-token", () -> "test-internal-token");
        registry.add("app.workspace-cache.enabled", () -> "false");
    }

    @Autowired MockMvc mvc;

    @Test
    void ownerCanCreateAndReadFeaturePositionProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));
        String organizationId = organizationFor(ownerId, "Feature Position Org", "feature-position-org");

        mvc.perform(get("/api/v1/organizations/" + organizationId + "/feature-position-profiles").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(0));

        mvc.perform(post("/api/v1/organizations/" + organizationId + "/feature-position-profiles").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tổ trưởng kỹ thuật\",\"code\":\"TECH_LEAD\",\"department\":\"Kỹ thuật\",\"description\":\"Quản lý nhóm kỹ thuật\",\"iconBg\":\"blue\",\"allowedFeatureKeys\":[\"overview\",\"attendance\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Tổ trưởng kỹ thuật"))
                .andExpect(jsonPath("$.data.isCustom").value(true))
                .andExpect(jsonPath("$.data.allowedFeatureKeys[1]").value("attendance"));

        mvc.perform(post("/api/v1/organizations/" + organizationId + "/feature-position-profiles").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tổ trưởng khác\",\"code\":\"TECH_LEAD\",\"iconBg\":\"blue\",\"allowedFeatureKeys\":[\"overview\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("feature_position_code_exists"));

        UUID memberId = UUID.randomUUID();
        var root = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        mvc.perform(put("/api/v1/organizations/" + organizationId + "/members").with(root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + memberId + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().is2xxSuccessful());
        var member = jwt().jwt(token -> token.claim("uid", memberId.toString()).claim("role", "USER"));
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/feature-position-profiles").with(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("organization_admin_required"));

        String profileId = mvc.perform(get("/api/v1/organizations/" + organizationId + "/feature-position-profiles").with(owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mvc.perform(patch("/api/v1/organizations/" + organizationId + "/feature-position-profiles/" + profileId).with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Đã cập nhật\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Đã cập nhật"));
        mvc.perform(delete("/api/v1/organizations/" + organizationId + "/feature-position-profiles/" + profileId).with(owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/feature-position-profiles").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    private String organizationFor(UUID ownerId, String name, String slug) throws Exception {
        var root = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        String organizationId = value(post("/api/v1/organizations"), root,
                "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}", "id");
        value(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/organizations/" + organizationId + "/members"), root,
                "{\"userId\":\"" + ownerId + "\",\"role\":\"OWNER\"}", "id");
        return organizationId;
    }

    private String value(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                         org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor,
                         String body, String field) throws Exception {
        String json = mvc.perform(builder.with(actor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\\\"" + field + "\\\":\\\"([^\\\"]+).*", "$1");
    }
}
