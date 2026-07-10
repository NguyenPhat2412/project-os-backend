package vn.uytinmang.projectos.identity.user;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiResponse;
import vn.uytinmang.projectos.identity.auth.AuthCookieService;

@RestController
@RequestMapping("/api/v1/users/me")
class UserController {
    private final UserProfileService users;
    private final AuthCookieService cookies;

    UserController(UserProfileService users, AuthCookieService cookies) {
        this.users = users;
        this.cookies = cookies;
    }

    @GetMapping("/profile")
    ApiResponse<JsonNode> profile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(users.profile(userId(jwt)));
    }

    @PatchMapping("/profile")
    ApiResponse<JsonNode> updateProfile(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        return ApiResponse.of(users.updateProfile(userId(jwt), body));
    }

    @GetMapping("/preferences/notifications")
    ApiResponse<JsonNode> notifications(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(users.notificationSettings(userId(jwt)));
    }

    @PutMapping("/preferences/notifications")
    ApiResponse<JsonNode> updateNotifications(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        return ApiResponse.of(users.updateNotificationSettings(userId(jwt), body));
    }

    @GetMapping("/preferences/appearance")
    ApiResponse<JsonNode> appearance(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(users.appearance(userId(jwt)));
    }

    @PutMapping("/preferences/appearance")
    ApiResponse<JsonNode> updateAppearance(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        return ApiResponse.of(users.updateAppearance(userId(jwt), body));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordRequest request) {
        users.changePassword(userId(jwt), request.currentPassword(), request.newPassword());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteRequest request,
                HttpServletResponse response) {
        users.deleteAccount(userId(jwt), request.password());
        cookies.clear(response);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }

    record PasswordRequest(@NotBlank String currentPassword,
                           @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    record DeleteRequest(@NotBlank String password) {
    }
}
