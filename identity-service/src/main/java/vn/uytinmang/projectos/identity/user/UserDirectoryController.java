package vn.uytinmang.projectos.identity.user;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.PageResponse;

@RestController
@RequestMapping("/api/v1/users/directory")
class UserDirectoryController {
    private final UserDirectoryService users;
    private final ProjectDirectoryClient projects;

    UserDirectoryController(UserDirectoryService users, ProjectDirectoryClient projects) {
        this.users = users;
        this.projects = projects;
    }

    @GetMapping
    PageResponse<UserDirectoryService.DirectoryUser> list(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "100") int size,
                                                           @RequestParam(required = false) String search,
                                                           @RequestParam(required = false) UUID projectId,
                                                           @AuthenticationPrincipal Jwt jwt) {
        boolean root = "ROOT_ADMIN".equalsIgnoreCase(jwt.getClaimAsString("role"));
        if (!root && projectId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "project_scope_required",
                    "projectId is required for the user directory");
        }
        Set<UUID> visibleUserIds = projectId == null ? null : projects.memberIds(projectId, jwt);
        return users.list(page, size, search, visibleUserIds);
    }
}
