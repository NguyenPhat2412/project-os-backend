package vn.uytinmang.projectos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.uytinmang.projectos.identity.user.UserAccountRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
        registry.add("app.google.redirect-uri", () -> "http://localhost:3000/api/v1/login/oauth2/code/google");
    }

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired JdbcTemplate jdbc;

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
    void passwordLoginPersistsRefreshTokenAndAllowsRotation() throws Exception {
        String email = "refresh-login-" + UUID.randomUUID() + "@example.test";
        String password = "Password123!";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                + "\",\"displayName\":\"Refresh Login\"}"))
                .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refresh = Arrays.stream(login.getResponse().getCookies())
                .filter(cookie -> "PROJECT_OS_REFRESH".equals(cookie.getName()) && cookie.getMaxAge() > 0)
                .findFirst().orElseThrow(() -> new AssertionError("Missing live refresh cookie"));
        Cookie csrf = login.getResponse().getCookie("XSRF-TOKEN");
        assertThat(refresh).isNotNull();
        assertThat(refresh.getPath()).isEqualTo("/");
        assertThat(csrf).isNotNull();

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie()
                        .exists("PROJECT_OS_REFRESH"));
    }

    @Test
    void googleLoginStartsAuthorizationCodeFlow() throws Exception {
        mvc.perform(get("/api/v1/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("accounts.google.com"),
                        org.hamcrest.Matchers.containsString("localhost:3000/api/v1/login/oauth2/code/google"))));
    }

    @Test
    void providerStatusLetsFrontendHideUnavailableOAuthButtons() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.google").value(true));
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

    @Test
    void rootAdminCanManageUsersAndDisableLogin() throws Exception {
        var adminId = java.util.UUID.randomUUID();
        var admin = jwt().jwt(token -> token.claim("uid", adminId.toString()).claim("role", "ROOT_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"));

        mvc.perform(post("/api/v1/admin/users").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"managed@example.com\",\"password\":\"StrongPass123!\","
                                + "\"displayName\":\"Managed User\",\"role\":\"USER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        var managed = users.findByEmail("managed@example.com").orElseThrow();
        mvc.perform(patch("/api/v1/admin/users/" + managed.getId()).with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROOT_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROOT_ADMIN"));

        mvc.perform(get("/api/v1/admin/users?search=managed").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));

        mvc.perform(delete("/api/v1/admin/users/" + managed.getId()).with(admin))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"managed@example.com\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUsersCanReadOnlyActiveDirectoryEntries() throws Exception {
        var root = jwt().jwt(token -> token.claim("uid", java.util.UUID.randomUUID().toString())
                .claim("role", "ROOT_ADMIN")).authorities(new SimpleGrantedAuthority("ROLE_ROOT_ADMIN"));
        var user = jwt().jwt(token -> token.claim("uid", java.util.UUID.randomUUID().toString())
                .claim("role", "USER")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"directory@example.com\",\"password\":\"StrongPass123!\","
                                + "\"displayName\":\"Directory User\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/users/directory?search=directory&page=0&size=10").with(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].email").value("directory@example.com"))
                .andExpect(jsonPath("$.data[0].role").doesNotExist());

        mvc.perform(get("/api/v1/users/directory").with(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("project_scope_required"));

        mvc.perform(get("/api/v1/users/directory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"))
                .andExpect(jsonPath("$.error.status").value(401))
                .andExpect(jsonPath("$.error.path").value("/api/v1/users/directory"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty())
                .andExpect(jsonPath("$.error.timestamp").isNotEmpty());
    }

    @Test
    void directorySearchIndexesAreInstalled() {
        var indexes = jdbc.queryForList("""
                select indexname from pg_indexes
                where schemaname = 'identity' and tablename = 'users'
                """, String.class);
        assertThat(indexes).contains(
                "users_active_directory_display_name_trgm_idx",
                "users_active_directory_email_trgm_idx");
    }

    @Test
    void rootRoleFromIssuedJwtAuthorizesAdminApi() throws Exception {
        var registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cookie-root@example.com\",\"password\":\"StrongPass123!\","
                                + "\"displayName\":\"Cookie Root\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var account = users.findByEmail("cookie-root@example.com").orElseThrow();
        account.setRole(vn.uytinmang.projectos.identity.user.UserAccount.Role.ROOT_ADMIN);
        users.saveAndFlush(account);

        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cookie-root@example.com\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(get("/api/v1/admin/users").cookie(cookie(login.getCookies(), "PROJECT_OS_ACCESS")))
                .andExpect(status().isOk());
    }

    private Cookie cookie(Cookie[] cookies, String name) {
        return Arrays.stream(cookies).filter(cookie -> name.equals(cookie.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing cookie " + name));
    }
}
