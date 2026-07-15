package vn.uytinmang.projectos.project.application;

import tools.jackson.databind.ObjectMapper;
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
import vn.uytinmang.projectos.project.permission.ProjectPermissionService;
import vn.uytinmang.projectos.project.web.ProjectController;
import vn.uytinmang.projectos.resource.OutboxPublisher;

@Service
public class ProjectApplicationService {
    private final ProjectRepository projects;
    private final OutboxPublisher outbox;
    private final ObjectMapper mapper;
    private final OrganizationAccessClient organizations;
    private final ProjectPermissionService permissions;

    public ProjectApplicationService(ProjectRepository projects, OutboxPublisher outbox, ObjectMapper mapper,
                                     OrganizationAccessClient organizations, ProjectPermissionService permissions) {
        this.projects = projects;
        this.outbox = outbox;
        this.mapper = mapper;
        this.organizations = organizations;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectController.ProjectView> list(int page, int size, UUID actorId, boolean rootAdmin) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "page must be >= 0 and size must be between 1 and 100");
        }
        var pageable = PageRequest.of(page, size);
        var result = (rootAdmin
                ? projects.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
                : projects.findAccessible(actorId, pageable)).map(ProjectController.ProjectView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProjectController.ProjectView get(UUID id, UUID actorId, boolean rootAdmin) {
        Project project = find(id);
        requireProjectAccess(project, actorId, rootAdmin, "read");
        return ProjectController.ProjectView.from(project);
    }

    @Transactional
    public ProjectController.ProjectView create(ProjectController.ProjectRequest request, UUID actorId, boolean rootAdmin) {
        String legacyId = clean(request.legacyId());
        if (legacyId != null && projects.findByLegacyId(legacyId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "legacy_id_exists", "Legacy project ID already exists");
        }
        organizations.requireProjectManager(request.organizationId(), actorId, rootAdmin);
        UUID effectiveOwnerId = rootAdmin && request.ownerId() != null ? request.ownerId() : actorId;
        Project project = new Project(request.name().trim(), clean(request.description()),
                status(request.status(), Project.Status.ACTIVE), defaulted(request.icon(), "P"),
                defaulted(request.color(), "from-violet-500 to-purple-600"), clean(request.currentSprint()),
                clean(request.quarter()), request.startDate(), request.endDate(), request.techStack(),
                request.teamSize(), effectiveOwnerId, request.organizationId());
        project.setLegacyId(legacyId);
        ProjectController.ProjectView view = ProjectController.ProjectView.from(projects.save(project));
        outbox.record(view.id(), "projects", view.id().toString(), "created", mapper.valueToTree(view), actorId);
        return view;
    }

    @Transactional
    public ProjectController.ProjectView update(UUID id, ProjectController.ProjectPatch request, UUID actorId, boolean rootAdmin) {
        Project project = find(id);
        requireProjectAccess(project, actorId, rootAdmin, "update");
        project.update(trimmed(request.name()), clean(request.description()), status(request.status(), null),
                trimmed(request.icon()), trimmed(request.color()), clean(request.currentSprint()),
                clean(request.quarter()), request.startDate(), request.endDate(), request.techStack(),
                request.teamSize());
        ProjectController.ProjectView view = ProjectController.ProjectView.from(project);
        outbox.record(id, "projects", id.toString(), "updated", mapper.valueToTree(view), actorId);
        return view;
    }

    @Transactional
    public void delete(UUID id, UUID actorId, boolean rootAdmin) {
        Project project = find(id);
        requireProjectAccess(project, actorId, rootAdmin, "delete");
        ProjectController.ProjectView view = ProjectController.ProjectView.from(project);
        outbox.record(id, "projects", id.toString(), "deleted", mapper.valueToTree(view), actorId);
        projects.delete(project);
    }

    private Project find(UUID id) {
        return projects.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "project_not_found", "Project not found"));
    }

    private void requireProjectAccess(Project project, UUID actorId, boolean rootAdmin, String action) {
        if (project.getOrganizationId() != null) {
            organizations.requireMember(project.getOrganizationId(), actorId, rootAdmin);
        }
        if (!rootAdmin && !permissions.allowed(project.getId(), actorId, "projects", action)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "project_access_denied", "Project access denied");
        }
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
