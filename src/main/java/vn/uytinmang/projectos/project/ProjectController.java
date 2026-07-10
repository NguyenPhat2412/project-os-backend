package vn.uytinmang.projectos.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
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

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    Page<ProjectResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return projectService.list(page, size);
    }

    @GetMapping("/{id}")
    ProjectResponse get(@PathVariable UUID id) {
        return projectService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ProjectResponse create(@Valid @RequestBody CreateProjectRequest request, @AuthenticationPrincipal Jwt jwt) {
        return projectService.create(request, UUID.fromString(jwt.getClaimAsString("uid")));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void delete(@PathVariable UUID id) {
        projectService.delete(id);
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String description,
            Project.Status status) {
    }

    public record UpdateProjectRequest(
            @Size(min = 1, max = 150) String name,
            @Size(max = 5000) String description,
            Project.Status status) {
    }

    public record ProjectResponse(
            UUID id,
            String name,
            String description,
            String status,
            OwnerResponse owner,
            Instant createdAt,
            Instant updatedAt) {
        static ProjectResponse from(Project project) {
            return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                    project.getStatus().name(), OwnerResponse.from(project), project.getCreatedAt(), project.getUpdatedAt());
        }
    }

    public record OwnerResponse(UUID id, String email, String displayName) {
        static OwnerResponse from(Project project) {
            return new OwnerResponse(project.getOwner().getId(), project.getOwner().getEmail(),
                    project.getOwner().getDisplayName());
        }
    }
}
