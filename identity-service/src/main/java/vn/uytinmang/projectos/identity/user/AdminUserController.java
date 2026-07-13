package vn.uytinmang.projectos.identity.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiResponse;
import vn.uytinmang.projectos.platform.api.PageResponse;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ROOT_ADMIN')")
class AdminUserController {
    private final AdminUserService users;

    AdminUserController(AdminUserService users) { this.users = users; }

    @GetMapping
    PageResponse<AdminUserService.UserView> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(required = false) String role,
                                                 @RequestParam(required = false) String status) {
        return users.list(page, size, search, role, status);
    }

    @GetMapping("/{id}")
    ApiResponse<AdminUserService.UserView> get(@PathVariable UUID id) {
        return ApiResponse.of(users.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AdminUserService.UserView> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.of(users.create(request));
    }

    @PatchMapping("/{id}")
    ApiResponse<AdminUserService.UserView> update(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.of(users.update(id, userId(jwt), request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        users.disable(id, userId(jwt));
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }

    record CreateRequest(@NotBlank @Email @Size(max = 254) String email,
                         @Size(min = 8, max = 72) String password,
                         @Size(min = 60, max = 60) String passwordHash,
                         @NotBlank @Size(max = 100) String displayName,
                         @NotBlank String role) {}

    record UpdateRequest(@Email @Size(max = 254) String email,
                         @Size(min = 1, max = 100) String displayName,
                         @Size(max = 2048) String avatarUrl,
                         String role,
                         String status,
                         @Size(min = 8, max = 72) String password) {}
}
