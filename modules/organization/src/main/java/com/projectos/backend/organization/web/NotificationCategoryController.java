package com.projectos.backend.organization.web;

import com.projectos.backend.organization.domain.NotificationCategoryService;
import com.projectos.backend.platform.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/notification-categories")
public class NotificationCategoryController {
    private final NotificationCategoryService service;

    public NotificationCategoryController(NotificationCategoryService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<CategoryView>> list(@PathVariable UUID organizationId,
                                         @RequestParam(defaultValue = "false") boolean includeInactive,
                                         @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.list(organizationId, includeInactive, actor(jwt), root(jwt)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CategoryView> create(@PathVariable UUID organizationId, @Valid @RequestBody CategoryRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.create(organizationId, request, actor(jwt), root(jwt)));
    }

    @PatchMapping("/{id}")
    ApiResponse<CategoryView> update(@PathVariable UUID organizationId, @PathVariable UUID id,
                                     @Valid @RequestBody CategoryPatch request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.update(organizationId, id, request, actor(jwt), root(jwt)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable UUID organizationId, @PathVariable UUID id,
                    @AuthenticationPrincipal Jwt jwt) {
        service.deactivate(organizationId, id, actor(jwt), root(jwt));
    }

    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private static boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }

    public record CategoryRequest(@NotBlank @Size(max = 80) String code,
                                  @NotBlank @Size(max = 160) String name,
                                  @Min(0) Integer displayOrder) {}

    public record CategoryPatch(@Size(max = 80) String code,
                                @Size(max = 160) String name,
                                Boolean isActive,
                                @Min(0) Integer displayOrder) {}

    public record CategoryView(UUID id, UUID organizationId, String code, String name, boolean isActive,
                               int displayOrder, UUID createdBy, Instant createdAt, Instant updatedAt) {}
}
