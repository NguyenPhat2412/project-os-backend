package vn.uytinmang.projectos.project;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.common.ApiException;
import vn.uytinmang.projectos.user.UserAccount;
import vn.uytinmang.projectos.user.UserAccountRepository;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final UserAccountRepository users;

    public ProjectService(ProjectRepository projects, UserAccountRepository users) {
        this.projects = projects;
        this.users = users;
    }

    @Transactional(readOnly = true)
    Page<ProjectController.ProjectResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
        }
        return projects.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(ProjectController.ProjectResponse::from);
    }

    @Transactional(readOnly = true)
    ProjectController.ProjectResponse get(UUID id) {
        return ProjectController.ProjectResponse.from(find(id));
    }

    @Transactional
    ProjectController.ProjectResponse create(ProjectController.CreateProjectRequest request, UUID actorId) {
        UserAccount owner = users.findById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        Project project = projects.save(new Project(request.name().trim(), clean(request.description()),
                request.status() == null ? Project.Status.PLANNED : request.status(), owner));
        return ProjectController.ProjectResponse.from(project);
    }

    @Transactional
    ProjectController.ProjectResponse update(UUID id, ProjectController.UpdateProjectRequest request) {
        if (request.name() != null && request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project name must not be blank");
        }
        Project project = find(id);
        project.update(request.name() == null ? null : request.name().trim(),
                clean(request.description()), request.description() != null, request.status());
        return ProjectController.ProjectResponse.from(project);
    }

    @Transactional
    void delete(UUID id) {
        projects.delete(find(id));
    }

    private Project find(UUID id) {
        return projects.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
