package com.projectos.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class KnowledgeResourceContractTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
        registry.add("app.outbox.enabled", () -> false);
        registry.add("app.rbac.enabled", () -> true);
    }

    @Autowired MockMvc mvc;

    @Test
    void documentCrudPersistsInKnowledgeSchema() throws Exception {
        UUID projectId = UUID.randomUUID();
        var actor = jwt().authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"))
                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        String path = "/api/v1/projects/" + projectId + "/documents";
        mvc.perform(post(path).with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legacyId\":\"DOC-01\",\"title\":\"Architecture\",\"type\":\"wiki\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("DOC-01"));
        mvc.perform(get(path + "/DOC-01").with(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Architecture"));
        mvc.perform(delete(path + "/DOC-01").with(actor)).andExpect(status().isNoContent());
        mvc.perform(get(path).with(actor)).andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void attachmentContentMustStayInsideItsProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        var actor = jwt().authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"))
                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "note".getBytes());

        mvc.perform(multipart("/api/v1/projects/" + projectId + "/attachments/content").file(file)
                        .param("storagePath", "projects/" + UUID.randomUUID() + "/documents/DOC-01")
                        .with(actor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_storage_path"));
    }

    @Test
    void nonRootCannotReadAProjectWithoutProjectPermission() throws Exception {
        UUID projectId = UUID.randomUUID();
        var actor = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "USER"));

        mvc.perform(get("/api/v1/projects/" + projectId + "/documents").with(actor))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("permission_service_unavailable"));
    }

    @Test
    void uploadRejectsActiveHtmlContentEvenWhenClientChangesItsMimeType() throws Exception {
        UUID projectId = UUID.randomUUID();
        var actor = jwt().authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"))
                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()).claim("role", "ROOT_ADMIN"));
        MockMultipartFile file = new MockMultipartFile("file", "payload.html", "text/plain",
                "<script>alert(1)</script>".getBytes());

        mvc.perform(multipart("/api/v1/projects/" + projectId + "/attachments/content").file(file)
                        .param("storagePath", "projects/" + projectId + "/documents").with(actor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("unsupported_file_type"));
    }
}
