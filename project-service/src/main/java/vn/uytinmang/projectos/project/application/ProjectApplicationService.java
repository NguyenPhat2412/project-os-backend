package vn.uytinmang.projectos.project.application;

import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
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
    private static final Pattern QUARTER_PATTERN = Pattern.compile("^Q([1-4])\\s+(\\d{4})$");

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
    public PageResponse<ProjectController.ProjectView> list(int page, int size, UUID organizationId,
                                                            UUID actorId, boolean rootAdmin) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "page must be >= 0 and size must be between 1 and 100");
        }
        var pageable = PageRequest.of(page, size);
        if (organizationId != null) organizations.requireMember(organizationId, actorId, rootAdmin);
        var result = rootAdmin
                ? organizationId == null ? projects.findAllByOrderByUpdatedAtDesc(pageable)
                : projects.findByOrganizationIdOrderByUpdatedAtDesc(organizationId, pageable)
                : organizationId == null ? projects.findAccessible(actorId, pageable)
                : projects.findAccessibleByOrganizationId(actorId, organizationId, pageable);
        var views = result.map(ProjectController.ProjectView::from);
        return PageResponse.of(views.getContent(), views.getNumber(), views.getSize(),
                views.getTotalElements(), views.getTotalPages());
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
        String currentSprint = clean(request.currentSprint());
        String quarter = clean(request.quarter());
        validateSchedule(quarter, request.startDate(), request.endDate());
        Project project = new Project(request.name().trim(), clean(request.description()),
                status(request.status(), Project.Status.ACTIVE), defaulted(request.icon(), "P"),
                defaulted(request.color(), "from-violet-500 to-purple-600"), currentSprint,
                quarter, request.startDate(), request.endDate(), request.techStack(),
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
        String currentSprint = clean(request.currentSprint());
        String quarter = clean(request.quarter());
        LocalDate effectiveQuarterStart = request.startDate() != null ? request.startDate() : project.getStartDate();
        LocalDate effectiveQuarterEnd = request.endDate() != null ? request.endDate() : project.getEndDate();
        String effectiveQuarter = request.quarter() != null ? quarter : project.getQuarter();
        validateSchedule(effectiveQuarter, effectiveQuarterStart, effectiveQuarterEnd);
        project.update(trimmed(request.name()), clean(request.description()), status(request.status(), null),
                trimmed(request.icon()), trimmed(request.color()), currentSprint,
                quarter, request.startDate(), request.endDate(), request.techStack(),
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

    private void validateSchedule(String quarter, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_project_schedule",
                    "Start date must be on or before end date");
        }
        if (quarter == null) return;

        Matcher matcher = QUARTER_PATTERN.matcher(quarter);
        if (!matcher.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_project_schedule",
                    "Quarter must use the format Q1 2026");
        }
        if (startDate == null || endDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_project_schedule",
                    "Projects assigned to a quarter require start and end dates");
        }

        int quarterNumber = Integer.parseInt(matcher.group(1));
        int year = Integer.parseInt(matcher.group(2));
        LocalDate quarterStart = LocalDate.of(year, (quarterNumber - 1) * 3 + 1, 1);
        LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
        if (startDate.isBefore(quarterStart) || endDate.isAfter(quarterEnd)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_project_schedule",
                    "Project dates must fall within " + quarter);
        }
    }
}
