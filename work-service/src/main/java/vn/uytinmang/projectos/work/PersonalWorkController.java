package vn.uytinmang.projectos.work;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.ApiResponse;
import vn.uytinmang.projectos.platform.api.PageResponse;
import vn.uytinmang.projectos.resource.ResourceApplicationService;
import vn.uytinmang.projectos.resource.ResourceRecord;
import vn.uytinmang.projectos.resource.ResourceRecordRepository;

@RestController
class PersonalWorkController {
    private final ResourceRecordRepository records;
    private final ResourceApplicationService resources;
    private final WorkAccessClient access;

    PersonalWorkController(ResourceRecordRepository records, ResourceApplicationService resources,
                           WorkAccessClient access) {
        this.records = records;
        this.resources = resources;
        this.access = access;
    }

    @GetMapping("/api/v1/me/tasks")
    PageResponse<JsonNode> myTasks(@RequestParam UUID projectId, @AuthenticationPrincipal Jwt jwt) {
        UUID actor = actor(jwt);
        access.requireProject(projectId, actor, "projects", "read", root(jwt));
        return page(records(projectId, "tasks").stream().filter(record -> ownedBy(record, actor)).toList());
    }

    @GetMapping("/api/v1/me/tasks/{taskId}")
    ApiResponse<JsonNode> myTask(@PathVariable String taskId, @RequestParam UUID projectId,
                                 @AuthenticationPrincipal Jwt jwt) {
        UUID actor = actor(jwt);
        access.requireProject(projectId, actor, "projects", "read", root(jwt));
        ResourceRecord record = require(projectId, "tasks", taskId);
        if (!ownedBy(record, actor)) throw forbidden();
        return ApiResponse.of(resources.view(record));
    }

    @PatchMapping("/api/v1/me/tasks/{taskId}/status")
    ApiResponse<JsonNode> updateMyTaskStatus(@PathVariable String taskId, @RequestParam UUID projectId,
            @Valid @RequestBody StatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID actor = actor(jwt);
        access.requireProject(projectId, actor, "projects", "read", root(jwt));
        ResourceRecord record = require(projectId, "tasks", taskId);
        if (!ownedBy(record, actor)) throw forbidden();
        ObjectNode patch = ((ObjectNode) record.getPayload()).objectNode();
        patch.put("status", request.status().trim().toLowerCase());
        return ApiResponse.of(resources.patch(projectId, "tasks", taskId, patch, actor));
    }

    @GetMapping("/api/v1/me/daily-reports")
    PageResponse<JsonNode> myReports(@RequestParam UUID projectId, @AuthenticationPrincipal Jwt jwt) {
        UUID actor = actor(jwt);
        access.requireProject(projectId, actor, "projects", "read", root(jwt));
        return page(records(projectId, "daily-reports").stream().filter(record -> ownedBy(record, actor)).toList());
    }

    @PostMapping("/api/v1/me/daily-reports")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<JsonNode> submitReport(@Valid @RequestBody DailyReportRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        UUID actor = actor(jwt);
        access.requireProject(request.projectId(), actor, "projects", "read", root(jwt));
        LocalDate date;
        try { date = LocalDate.parse(request.date()); }
        catch (DateTimeParseException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_date", "date must use ISO-8601"); }
        ObjectNode body = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        body.put("legacyId", "DAILY-" + actor + "-" + date);
        body.put("userId", actor.toString());
        body.put("date", date.toString());
        body.put("summary", request.summary().trim());
        if (request.blockers() != null) body.put("blockers", request.blockers().trim());
        if (request.nextPlan() != null) body.put("nextPlan", request.nextPlan().trim());
        return ApiResponse.of(resources.create(request.projectId(), "daily-reports", body, actor));
    }

    @GetMapping("/api/v1/manager/team/tasks")
    PageResponse<JsonNode> teamTasks(@RequestParam UUID organizationId, @RequestParam UUID projectId,
                                     @AuthenticationPrincipal Jwt jwt) {
        UUID actor = actor(jwt);
        access.requireProject(projectId, actor, "tasks", "read", root(jwt));
        if (root(jwt)) return page(records(projectId, "tasks"));
        Set<UUID> reports = access.directReportUsers(organizationId, actor, root(jwt));
        return page(records(projectId, "tasks").stream().filter(record -> assignedTo(record, reports)).toList());
    }

    @PostMapping("/api/v1/manager/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<JsonNode> createTeamTask(@RequestBody ObjectNode body, @AuthenticationPrincipal Jwt jwt) {
        UUID actor = actor(jwt);
        UUID projectId = requiredUuid(body, "projectId");
        UUID organizationId = requiredUuid(body, "organizationId");
        UUID assigneeId = requiredUuid(body, "assigneeId");
        access.requireProject(projectId, actor, "tasks", "create", root(jwt));
        if (!root(jwt) && !access.directReportUsers(organizationId, actor, false).contains(assigneeId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "assignee_scope_denied",
                    "A manager can only assign tasks to direct reports");
        }
        ObjectNode payload = body.deepCopy();
        payload.remove("organizationId");
        return ApiResponse.of(resources.create(projectId, "tasks", payload, actor));
    }

    @GetMapping("/api/v1/manager/daily-reports")
    PageResponse<JsonNode> teamReports(@RequestParam UUID organizationId, @RequestParam UUID projectId,
            @RequestParam(required = false) String date, @AuthenticationPrincipal Jwt jwt) {
        UUID actor = actor(jwt);
        access.requireProject(projectId, actor, "daily-reports", "read", root(jwt));
        if (root(jwt)) return page(records(projectId, "daily-reports").stream()
                .filter(record -> date == null || date.equals(record.getPayload().path("date").asText())).toList());
        Set<UUID> reports = access.directReportUsers(organizationId, actor, root(jwt));
        return page(records(projectId, "daily-reports").stream()
                .filter(record -> assignedTo(record, reports))
                .filter(record -> date == null || date.equals(record.getPayload().path("date").asText()))
                .toList());
    }

    private List<ResourceRecord> records(UUID projectId, String type) {
        return records.findAllByProjectIdAndResourceTypeOrderByCreatedAtAsc(projectId, type);
    }
    private PageResponse<JsonNode> page(List<ResourceRecord> values) {
        List<JsonNode> data = values.stream().map(resources::view).toList();
        return PageResponse.of(data, 0, Math.max(1, data.size()), data.size(), data.isEmpty() ? 0 : 1);
    }
    private ResourceRecord require(UUID projectId, String type, String id) {
        try {
            return records.findByProjectIdAndResourceTypeAndId(projectId, type, UUID.fromString(id))
                    .orElseGet(() -> records.findByProjectIdAndResourceTypeAndLegacyId(projectId, type, id)
                            .orElseThrow(this::notFound));
        } catch (IllegalArgumentException exception) {
            return records.findByProjectIdAndResourceTypeAndLegacyId(projectId, type, id).orElseThrow(this::notFound);
        }
    }
    private boolean ownedBy(ResourceRecord record, UUID actor) {
        String value = record.getPayload().path("assigneeId").asText(record.getCreatedBy().toString());
        if ("daily-reports".equals(record.getResourceType())) value = record.getPayload().path("userId").asText();
        return actor.toString().equals(value);
    }
    private boolean assignedTo(ResourceRecord record, Set<UUID> users) {
        String value = record.getPayload().path("assigneeId")
                .asText(record.getPayload().path("userId").asText());
        try { return users.contains(UUID.fromString(value)); } catch (IllegalArgumentException exception) { return false; }
    }
    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }
    private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "task_scope_denied", "You can only access your assigned tasks"); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "record_not_found", "Record not found"); }
    private UUID requiredUuid(JsonNode body, String field) {
        try { return UUID.fromString(body.path(field).asText()); }
        catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + field, field + " must be a UUID");
        }
    }

    record StatusRequest(@NotBlank @Size(max = 40) String status) {}
    record DailyReportRequest(@NotNull UUID projectId, @NotBlank String date,
                              @NotBlank @Size(max = 4000) String summary,
                              @Size(max = 2000) String blockers, @Size(max = 2000) String nextPlan) {}
}
