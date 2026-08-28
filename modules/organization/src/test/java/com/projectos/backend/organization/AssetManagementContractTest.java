package com.projectos.backend.organization;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AssetManagementContractTest {
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
    void ownerCanCreateAssetAndHandoverItToAnEmployee() throws Exception {
        UUID ownerId = UUID.randomUUID();
        var root = jwt().jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        String organizationId = value(post("/api/v1/organizations"), root,
                "{\"name\":\"Asset Contract Org\",\"slug\":\"asset-contract-org\"}", "id");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/organizations/" + organizationId + "/members").with(root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + ownerId + "\",\"role\":\"OWNER\"}"))
                .andExpect(status().is2xxSuccessful());
        var owner = jwt().jwt(token -> token.claim("uid", ownerId.toString()).claim("role", "USER"));

        String employeeId = value(post("/api/v1/organizations/" + organizationId + "/employees"), owner,
                "{\"fullName\":\"Nguyễn Văn A\",\"email\":\"asset.employee@example.com\",\"code\":\"NV-ASSET-01\"}", "id");
        mvc.perform(get("/api/v1/organizations/" + organizationId + "/assets").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(0));

        String assetId = value(post("/api/v1/organizations/" + organizationId + "/assets"), owner,
                "{\"code\":\"TS-ASSET-01\",\"name\":\"Laptop công việc\",\"category\":\"it_device\",\"status\":\"available\"}", "id");
        mvc.perform(post("/api/v1/organizations/" + organizationId + "/assets/handovers").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"" + employeeId + "\",\"assetIds\":[\"" + assetId + "\"],\"purpose\":\"Cấp phát công việc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("pending"));

        mvc.perform(get("/api/v1/organizations/" + organizationId + "/employees/" + employeeId + "/assets").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    private String value(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                         JwtRequestPostProcessor actor,
                         String body, String field) throws Exception {
        String json = mvc.perform(builder.with(actor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\\\"" + field + "\\\":\\\"([^\\\"]+).*", "$1");
    }
}
