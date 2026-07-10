package vn.uytinmang.projectos.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
