package vn.uytinmang.projectos.activity;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiResponse;
import vn.uytinmang.projectos.platform.api.PageResponse;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/activities")
class ActivityController {
    private final ScopedActivityService activities;

    ActivityController(ScopedActivityService activities) {
        this.activities = activities;
    }

    @GetMapping
    PageResponse<ScopedActivityService.ActivityView> list(@PathVariable UUID projectId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int size,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return activities.listForActor(projectId, actor(jwt), page, size);
    }

    @GetMapping("/{activityId}")
    ApiResponse<ScopedActivityService.ActivityView> get(@PathVariable UUID projectId,
                                                        @PathVariable UUID activityId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(activities.getForActor(projectId, actor(jwt), activityId));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }
}
