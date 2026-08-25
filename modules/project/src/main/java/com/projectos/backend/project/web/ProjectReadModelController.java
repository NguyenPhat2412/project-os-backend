package com.projectos.backend.project.web;

import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.projectos.backend.platform.api.ApiResponse;
import com.projectos.backend.project.application.ProjectApplicationService;
import com.projectos.backend.resource.ResourceApplicationService;
import com.projectos.backend.resource.ResourceRecord;
import com.projectos.backend.resource.ResourceRecordRepository;

/**
 * Project read models are assembled in-process from persisted resource records.
 * They are intentionally read-only; mutations remain owned by the resource API.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/read-model")
public class ProjectReadModelController {
    private static final Set<String> REPORT_RESOURCES = Set.of("tasks", "bugs", "risks");

    private final ProjectApplicationService projects;
    private final ResourceRecordRepository records;
    private final ResourceApplicationService resources;

    public ProjectReadModelController(ProjectApplicationService projects,
                                      ResourceRecordRepository records,
                                      ResourceApplicationService resources) {
        this.projects = projects;
        this.records = records;
        this.resources = resources;
    }

    @GetMapping("/dashboard")
    ApiResponse<Map<String, Object>> dashboard(@PathVariable UUID projectId,
                                                @AuthenticationPrincipal Jwt jwt) {
        authorize(projectId, jwt);
        List<JsonNode> tasks = views(projectId, "tasks");
        List<JsonNode> bugs = views(projectId, "bugs");
        List<JsonNode> risks = views(projectId, "risks");
        List<JsonNode> meetings = views(projectId, "meetings");
        List<JsonNode> team = views(projectId, "members");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tasks", tasks.size());
        summary.put("bugs", bugs.size());
        summary.put("risks", risks.size());
        summary.put("members", team.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meetings", meetings);
        result.put("tasks", tasks);
        result.put("bugs", bugs);
        result.put("risks", risks);
        result.put("team", team);
        result.put("summary", summary);
        return ApiResponse.of(result);
    }

    @GetMapping("/reports/{resource}")
    ApiResponse<Map<String, Object>> report(@PathVariable UUID projectId,
                                             @PathVariable String resource,
                                             @AuthenticationPrincipal Jwt jwt) {
        authorize(projectId, jwt);
        if (!REPORT_RESOURCES.contains(resource)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Unknown report resource");
        }
        List<JsonNode> items = views(projectId, resource);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("members", views(projectId, "members"));
        result.put("summary", summary(items));
        return ApiResponse.of(result);
    }

    @GetMapping("/workload")
    ApiResponse<Map<String, Object>> workload(@PathVariable UUID projectId,
                                              @AuthenticationPrincipal Jwt jwt) {
        authorize(projectId, jwt);
        List<JsonNode> tasks = views(projectId, "tasks");
        Map<String, Map<String, Object>> byAssignee = new LinkedHashMap<>();
        for (JsonNode task : tasks) {
            String assigneeId = task.path("assigneeId").asText("");
            if (assigneeId.isBlank()) continue;
            Map<String, Object> entry = byAssignee.computeIfAbsent(assigneeId, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("assigneeId", assigneeId);
                value.put("tasks", 0);
                value.put("points", 0);
                value.put("status", new LinkedHashMap<String, Integer>());
                return value;
            });
            entry.put("tasks", ((Integer) entry.get("tasks")) + 1);
            entry.put("points", ((Integer) entry.get("points")) + task.path("points").asInt(0));
            @SuppressWarnings("unchecked")
            Map<String, Integer> statuses = (Map<String, Integer>) entry.get("status");
            String status = task.path("status").asText("");
            if (!status.isBlank()) statuses.merge(status, 1, Integer::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("members", views(projectId, "members"));
        result.put("workload", new ArrayList<>(byAssignee.values()));
        return ApiResponse.of(result);
    }

    private void authorize(UUID projectId, Jwt jwt) {
        projects.get(projectId, actor(jwt), root(jwt));
    }

    private List<JsonNode> views(UUID projectId, String resourceType) {
        return records.findAllByProjectIdAndResourceTypeOrderByCreatedAtAsc(projectId, resourceType)
                .stream().map(resources::view).toList();
    }

    private Map<String, Object> summary(List<JsonNode> items) {
        Map<String, Integer> status = new LinkedHashMap<>();
        Map<String, Integer> priority = new LinkedHashMap<>();
        Map<String, Integer> level = new LinkedHashMap<>();
        items.forEach(item -> {
            increment(status, item.path("status").asText(""));
            increment(priority, item.path("priority").asText(""));
            increment(level, item.path("level").asText(""));
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("status", status);
        result.put("priority", priority);
        result.put("level", level);
        return result;
    }

    private void increment(Map<String, Integer> counts, String value) {
        if (!value.isBlank()) counts.merge(value, 1, Integer::sum);
    }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }
}
