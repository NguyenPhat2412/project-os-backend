package vn.uytinmang.projectos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.uytinmang.projectos.user.UserAccount;
import vn.uytinmang.projectos.user.UserAccountRepository;

@SpringBootTest(properties = "app.jwt.secret=test-jwt-secret-with-at-least-32-characters")
@AutoConfigureMockMvc
@Transactional
class ProjectApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void authAndProjectPermissionsWorkEndToEnd() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String userEmail = "user-" + suffix + "@example.com";
        String adminEmail = "admin-" + suffix + "@example.com";
        String password = "StrongPass123!";

        String registerBody = objectMapper.writeValueAsString(new RegisterBody(userEmail, password, "Normal User"));
        String userToken = token(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn().getResponse().getContentAsString());

        users.save(new UserAccount(adminEmail, passwordEncoder.encode(password), "Administrator", UserAccount.Role.ADMIN));
        String loginBody = objectMapper.writeValueAsString(new LoginBody(adminEmail, password));
        String adminToken = token(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString());

        String createBody = "{\"name\":\"Postman Project\",\"description\":\"Stored in PostgreSQL\",\"status\":\"ACTIVE\"}";
        mvc.perform(post("/api/projects").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/projects").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(created).get("id").asText();

        mvc.perform(get("/api/projects/{id}", projectId).header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Postman Project"));

        mvc.perform(patch("/api/projects/{id}", projectId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(delete("/api/projects/{id}", projectId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/{id}", projectId).header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound());
    }

    private String token(String body) throws Exception {
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    record RegisterBody(String email, String password, String displayName) {
    }

    record LoginBody(String email, String password) {
    }
}
