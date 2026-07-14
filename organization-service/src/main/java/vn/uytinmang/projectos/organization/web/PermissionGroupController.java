package vn.uytinmang.projectos.organization.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.organization.domain.PermissionGroupService;
import vn.uytinmang.projectos.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/permission-groups")
public class PermissionGroupController {
    private final PermissionGroupService service;
    PermissionGroupController(PermissionGroupService service) { this.service = service; }

    @GetMapping
    ApiResponse<List<PermissionGroupService.GroupView>> list(@PathVariable UUID organizationId,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.list(organizationId, actor(jwt), root(jwt)));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PermissionGroupService.GroupView> create(@PathVariable UUID organizationId,
            @Valid @RequestBody GroupRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.create(organizationId, request, actor(jwt), root(jwt)));
    }

    @PatchMapping("/{groupId}")
    ApiResponse<PermissionGroupService.GroupView> update(@PathVariable UUID organizationId,
            @PathVariable UUID groupId, @Valid @RequestBody GroupPatch request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.update(organizationId, groupId, request, actor(jwt), root(jwt)));
    }

    @DeleteMapping("/{groupId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID organizationId, @PathVariable UUID groupId, @AuthenticationPrincipal Jwt jwt) {
        service.delete(organizationId, groupId, actor(jwt), root(jwt));
    }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }

    public record GroupRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
                               Set<String> modules, Set<UUID> memberIds) {}
    public record GroupPatch(@Size(min = 1, max = 100) String name, @Size(max = 500) String description,
                             Set<String> modules, Set<UUID> memberIds) {}
}
