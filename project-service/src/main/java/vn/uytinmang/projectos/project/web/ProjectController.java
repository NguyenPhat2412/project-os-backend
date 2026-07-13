package vn.uytinmang.projectos.project.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
import vn.uytinmang.projectos.project.application.ProjectApplicationService;
import vn.uytinmang.projectos.project.domain.Project;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectApplicationService service;

    public ProjectController(ProjectApplicationService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ProjectView> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.list(page, size, UUID.fromString(jwt.getClaimAsString("uid")),
                "ROOT_ADMIN".equals(jwt.getClaimAsString("role")));
    }

    @GetMapping("/{id}")
    ApiResponse<ProjectView> get(@PathVariable UUID id) { return ApiResponse.of(service.get(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ROOT_ADMIN')")
    ApiResponse<ProjectView> create(@Valid @RequestBody ProjectRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.create(request, UUID.fromString(jwt.getClaimAsString("uid"))));
    }

    @PatchMapping("/{id}")
    ApiResponse<ProjectView> update(@PathVariable UUID id, @Valid @RequestBody ProjectPatch request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.update(id, request, UUID.fromString(jwt.getClaimAsString("uid"))));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(id, UUID.fromString(jwt.getClaimAsString("uid")));
    }

    public record ProjectRequest(@NotBlank @Size(max = 150) String name,
                                 @Size(max = 5000) String description,
                                 String status, @Size(max = 50) String icon,
                                 @Size(max = 100) String color,
                                 @Size(max = 100) String currentSprint,
                                 @Size(max = 30) String quarter,
                                 LocalDate startDate, LocalDate endDate,
                                 List<@Size(max = 100) String> techStack,
                                 @Min(0) Integer teamSize,
                                 UUID ownerId,
                                 UUID organizationId,
                                 @Size(max = 255) String legacyId) {
    }

    public record ProjectPatch(@Size(min = 1, max = 150) String name,
                               @Size(max = 5000) String description,
                               String status, @Size(max = 50) String icon,
                               @Size(max = 100) String color,
                               @Size(max = 100) String currentSprint,
                               @Size(max = 30) String quarter,
                               LocalDate startDate, LocalDate endDate,
                               List<@Size(max = 100) String> techStack,
                               @Min(0) Integer teamSize) {
    }

    public record ProjectView(UUID id, String legacyId, String name, String description, String status,
                              String icon, String color, String currentSprint, String quarter,
                              LocalDate startDate, LocalDate endDate, List<String> techStack,
                              Integer teamSize, UUID ownerId, UUID organizationId, Instant createdAt, Instant updatedAt) {
        public static ProjectView from(Project project) {
            return new ProjectView(project.getId(), project.getLegacyId(), project.getName(),
                    project.getDescription(), project.getStatus().name().toLowerCase(Locale.ROOT),
                    project.getIcon(), project.getColor(), project.getCurrentSprint(), project.getQuarter(),
                    project.getStartDate(), project.getEndDate(), project.getTechStack(), project.getTeamSize(),
                    project.getOwnerId(), project.getOrganizationId(), project.getCreatedAt(), project.getUpdatedAt());
        }
    }
}
