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
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.ApiResponse;
import vn.uytinmang.projectos.project.domain.ProjectRepository;

@RestController
@RequestMapping("/api/v1/internal/projects")
class InternalProjectScopeController {
    private final ProjectRepository projects;
    private final byte[] internalToken;

    InternalProjectScopeController(ProjectRepository projects,
                                   @Value("${app.rbac.internal-token}") String internalToken) {
        this.projects = projects;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/{projectId}/scope")
    ApiResponse<ProjectScope> scope(@PathVariable UUID projectId,
                                    @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        byte[] candidate = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalToken, candidate)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "invalid_internal_token", "Invalid internal token");
        }
        var project = projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project_not_found", "Project not found"));
        return ApiResponse.of(new ProjectScope(project.getOrganizationId()));
    }

    record ProjectScope(UUID organizationId) {
    }
}
