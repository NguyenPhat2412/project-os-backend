package vn.uytinmang.projectos.project.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.PageResponse;
import vn.uytinmang.projectos.project.domain.Project;
import vn.uytinmang.projectos.project.domain.ProjectRepository;
import vn.uytinmang.projectos.project.web.ProjectController;

@Service
public class ProjectApplicationService {
    private final ProjectRepository projects;

    public ProjectApplicationService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectController.ProjectView> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "page must be >= 0 and size must be between 1 and 100");
        }
        var result = projects.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "updatedAt"))).map(ProjectController.ProjectView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProjectController.ProjectView get(UUID id) {
        return ProjectController.ProjectView.from(find(id));
    }

    @Transactional
    public ProjectController.ProjectView create(ProjectController.ProjectRequest request, UUID ownerId) {
        Project project = new Project(request.name().trim(), clean(request.description()),
                status(request.status(), Project.Status.ACTIVE), defaulted(request.icon(), "P"),
                defaulted(request.color(), "from-violet-500 to-purple-600"), clean(request.currentSprint()),
                clean(request.quarter()), request.startDate(), request.endDate(), request.techStack(),
                request.teamSize(), ownerId);
        return ProjectController.ProjectView.from(projects.save(project));
    }

    @Transactional
    public ProjectController.ProjectView update(UUID id, ProjectController.ProjectPatch request) {
        Project project = find(id);
        project.update(trimmed(request.name()), clean(request.description()), status(request.status(), null),
                trimmed(request.icon()), trimmed(request.color()), clean(request.currentSprint()),
                clean(request.quarter()), request.startDate(), request.endDate(), request.techStack(),
                request.teamSize());
        return ProjectController.ProjectView.from(project);
    }

    @Transactional
    public void delete(UUID id) {
        projects.delete(find(id));
    }

    private Project find(UUID id) {
        return projects.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "project_not_found", "Project not found"));
    }

    private Project.Status status(String status, Project.Status fallback) {
        if (status == null) return fallback;
        try {
            return Project.Status.valueOf(status.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_status", "Invalid project status");
        }
    }

    private String trimmed(String value) { return value == null ? null : value.trim(); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String defaulted(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
}
