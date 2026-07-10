package vn.uytinmang.projectos.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
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
class AuthContractTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
        registry.add("app.cookie.secure", () -> false);
        registry.add("app.google.client-id", () -> "test-google-client-id");
        registry.add("app.google.client-secret", () -> "test-google-client-secret");
    }

    @Autowired MockMvc mvc;

    @Test
    void registerAndLoginIssueHttpOnlyCookies() throws Exception {
        String body = "{\"email\":\"owner@example.com\",\"password\":\"StrongPass123!\",\"displayName\":\"Owner\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.email").value("owner@example.com"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("PROJECT_OS_ACCESS="),
                        org.hamcrest.Matchers.containsString("HttpOnly"))))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("XSRF-TOKEN="))));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.displayName").value("Owner"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void refreshCookieRequiresMatchingCsrfHeader() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie("PROJECT_OS_REFRESH", "invalid")))
                .andExpect(status().isForbidden());
    }

    @Test
    void googleLoginStartsAuthorizationCodeFlow() throws Exception {
        mvc.perform(get("/api/v1/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("accounts.google.com")));
    }

    @Test
    void profilePreferencesAndPasswordPersistInIdentitySchema() throws Exception {
        String email = "profile@example.com";
        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"StrongPass123!\",\"displayName\":\"Before\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        Cookie access = cookie(response.getCookies(), "PROJECT_OS_ACCESS");
        Cookie csrf = cookie(response.getCookies(), "XSRF-TOKEN");

        mvc.perform(patch("/api/v1/users/me/profile").cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"After\",\"department\":\"Engineering\","
                                + "\"skills\":[\"Java\",\"PostgreSQL\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("After"))
                .andExpect(jsonPath("$.data.skills[1]").value("PostgreSQL"));

        mvc.perform(get("/api/v1/users/me/profile").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.department").value("Engineering"));

        mvc.perform(put("/api/v1/users/me/preferences/notifications").cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailProjects\":false,\"channelPush\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelPush").value(true));

        mvc.perform(post("/api/v1/users/me/password").cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"StrongPass123!\",\"newPassword\":\"ChangedPass456!\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"ChangedPass456!\"}"))
                .andExpect(status().isOk());
    }

    private Cookie cookie(Cookie[] cookies, String name) {
        return Arrays.stream(cookies).filter(cookie -> name.equals(cookie.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing cookie " + name));
    }
}
