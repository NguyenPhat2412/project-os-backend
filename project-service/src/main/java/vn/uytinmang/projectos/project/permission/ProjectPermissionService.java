package vn.uytinmang.projectos.project.permission;

import tools.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.project.domain.ProjectRepository;
import vn.uytinmang.projectos.resource.ResourceRecord;
import vn.uytinmang.projectos.resource.ResourceRecordRepository;

@Service
public class ProjectPermissionService {
    private static final Set<String> ADMIN_RESOURCES = Set.of(
            "projects", "members", "roles", "role-assignments", "settings", "tasks-all");
    private static final Set<String> SCOPED_RESOURCES = Set.of("tasks", "tasks-all", "daily-reports");
    private final ProjectRepository projects;
    private final ResourceRecordRepository records;

    ProjectPermissionService(ProjectRepository projects, ResourceRecordRepository records) {
        this.projects = projects;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public boolean allowed(UUID projectId, UUID actorId, String resource, String action) {
        var project = projects.findById(projectId);
        if (project.isEmpty()) return false;
        if (actorId.equals(project.get().getOwnerId())) return true;

        Set<String> assignedRoles = assignedRoles(projectId, actorId);
        if ("read".equals(action) && !SCOPED_RESOURCES.contains(normalize(resource))) {
            return !assignedRoles.isEmpty() || isMember(projectId, actorId);
        }
        if (assignedRoles.stream().anyMatch(this::isProjectAdmin)) return true;
        String permission = normalize(resource) + ":" + normalize(action);
        Map<String, Set<String>> definitions = roleDefinitions(projectId);
        for (String role : assignedRoles) {
            Set<String> explicit = definitions.getOrDefault(role, Set.of());
            if (explicit.contains("*:*") || explicit.contains(normalize(resource) + ":*")
                    || explicit.contains(permission) || defaultPermission(role, resource, action)) return true;
        }
        return false;
    }

    private boolean isMember(UUID projectId, UUID actorId) {
        for (ResourceRecord record : records.findAllByProjectIdAndResourceType(projectId, "members",
                PageRequest.of(0, 500)).getContent()) {
            if (actorId.toString().equals(first(record.getPayload(), "uid", "memberId", "userId"))) return true;
        }
        return false;
    }

    private Set<String> assignedRoles(UUID projectId, UUID actorId) {
        Set<String> roles = new HashSet<>();
        for (ResourceRecord record : records.findAllByProjectIdAndResourceType(projectId, "role-assignments",
                PageRequest.of(0, 500)).getContent()) {
            JsonNode payload = record.getPayload();
            String member = first(payload, "uid", "memberId", "userId");
            if (!actorId.toString().equals(member)) continue;
            JsonNode values = payload.get("roles");
            if (values != null && values.isArray()) values.forEach(value -> roles.add(normalize(value.asText())));
            if (payload.hasNonNull("role")) roles.add(normalize(payload.get("role").asText()));
        }
        return roles;
    }

    private Map<String, Set<String>> roleDefinitions(UUID projectId) {
        Map<String, Set<String>> result = new HashMap<>();
        for (ResourceRecord record : records.findAllByProjectIdAndResourceType(projectId, "roles",
                PageRequest.of(0, 500)).getContent()) {
            Set<String> keys = new HashSet<>();
            if (record.getLegacyId() != null) keys.add(normalize(record.getLegacyId()));
            JsonNode payload = record.getPayload();
            if (payload.hasNonNull("id")) keys.add(normalize(payload.get("id").asText()));
            if (payload.hasNonNull("name")) keys.add(normalize(payload.get("name").asText()));
            Set<String> permissions = new HashSet<>();
            JsonNode values = payload.get("permissions");
            if (values != null && values.isArray()) {
                values.forEach(value -> permissions.add(normalizePermission(value.asText())));
            }
            keys.forEach(key -> result.put(key, Set.copyOf(permissions)));
        }
        return result;
    }

    private boolean defaultPermission(String role, String resource, String action) {
        String normalizedResource = normalize(resource);
        if (ADMIN_RESOURCES.contains(normalizedResource)) return false;
        return switch (role) {
            case "pm", "project-manager" -> true;
            case "developer" -> Set.of("task-columns", "sprints", "bugs", "comments",
                    "documents", "folders", "wikis", "attachments", "activities").contains(normalizedResource);
            case "qc", "qa" -> Set.of("bugs", "bug-columns", "comments", "activities")
                    .contains(normalizedResource);
            case "ba", "business-analyst" -> Set.of("epics", "user-stories", "documents", "wikis",
                    "comments", "activities").contains(normalizedResource);
            default -> false;
        };
    }

    private boolean isProjectAdmin(String role) {
        return Set.of("project-admin", "projectadmin", "admin", "owner").contains(role);
    }

    private String first(JsonNode body, String... fields) {
        for (String field : fields) if (body.hasNonNull(field)) return body.get(field).asText();
        return null;
    }

    private String normalizePermission(String permission) {
        String[] parts = permission.split(":", 2);
        return parts.length == 2 ? normalize(parts[0]) + ":" + normalize(parts[1]) : normalize(permission);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }
}
