package vn.uytinmang.projectos.project.permission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/internal/projects/{projectId}/permissions")
class ProjectPermissionController {
    private final ProjectPermissionService permissions;
    private final byte[] internalToken;

    ProjectPermissionController(ProjectPermissionService permissions,
                                @Value("${app.rbac.internal-token}") String internalToken) {
        this.permissions = permissions;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/check")
    ApiResponse<PermissionDecision> check(@PathVariable UUID projectId, @RequestParam UUID actorId,
                                         @RequestParam String resource, @RequestParam String action,
                                         @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        byte[] candidate = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalToken, candidate)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "invalid_internal_token", "Invalid internal token");
        }
        return ApiResponse.of(new PermissionDecision(permissions.allowed(projectId, actorId, resource, action)));
    }

    record PermissionDecision(boolean allowed) {
    }
}
