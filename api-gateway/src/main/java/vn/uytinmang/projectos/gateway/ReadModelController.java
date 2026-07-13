package vn.uytinmang.projectos.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import vn.uytinmang.projectos.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/read-model")
public class ReadModelController {
    private static final Set<String> REPORT_RESOURCES = Set.of("tasks", "bugs", "risks");
    private final RestClient projects;
    private final RestClient work;
    private final RestClient operations;
    private final ExecutorService executor;
    private final ReadModelCache cache;

    public ReadModelController(@Value("${PROJECT_SERVICE_URL:http://localhost:8082}") String projectUrl,
                               @Value("${WORK_SERVICE_URL:http://localhost:8083}") String workUrl,
                               @Value("${OPERATIONS_SERVICE_URL:http://localhost:8084}") String operationsUrl,
                               ObjectProvider<RestClient.Builder> clientBuilders,
                               ExecutorService executor, ReadModelCache cache) {
        this.projects = client(clientBuilders, projectUrl);
        this.work = client(clientBuilders, workUrl);
        this.operations = client(clientBuilders, operationsUrl);
        this.executor = executor;
        this.cache = cache;
    }

    @GetMapping("/dashboard")
    ResponseEntity<ApiResponse<DashboardReadModel>> dashboard(@PathVariable UUID projectId,
                                                               @AuthenticationPrincipal Jwt jwt,
                                                               HttpServletRequest request) {
        String key = cache.key(projectId, subject(jwt), "dashboard");
        var cached = cache.get(key, DashboardReadModel.class);
        if (cached.isPresent()) return response(cached.get(), "HIT");

        var meetingsFuture = async(() -> collection(operations, projectId, "meetings", request));
        var tasksFuture = async(() -> collection(work, projectId, "tasks", request));
        var columnsFuture = async(() -> collection(work, projectId, "task-columns", request));
        var bugsFuture = async(() -> collection(work, projectId, "bugs", request));
        var risksFuture = async(() -> collection(operations, projectId, "risks", request));
        var membersFuture = async(() -> collection(projects, projectId, "members", request));

        JsonNode meetings = join(meetingsFuture);
        JsonNode tasks = join(tasksFuture);
        JsonNode taskColumns = join(columnsFuture);
        JsonNode bugs = join(bugsFuture);
        JsonNode risks = join(risksFuture);
        JsonNode members = join(membersFuture);
        DashboardReadModel model = new DashboardReadModel(meetings, tasks, taskColumns, bugs, risks, members,
                new DashboardSummary(tasks.size(), bugs.size(), risks.size(), members.size(),
                        counts(tasks, "status"), counts(bugs, "status"), counts(risks, "status")));
        return response(model, cache.put(key, model) ? "MISS" : "BYPASS");
    }

    @GetMapping("/workload")
    ResponseEntity<ApiResponse<WorkloadReadModel>> workload(@PathVariable UUID projectId,
                                                             @AuthenticationPrincipal Jwt jwt,
                                                             HttpServletRequest request) {
        String key = cache.key(projectId, subject(jwt), "workload");
        var cached = cache.get(key, WorkloadReadModel.class);
        if (cached.isPresent()) return response(cached.get(), "HIT");

        var tasksFuture = async(() -> collection(work, projectId, "tasks", request));
        var membersFuture = async(() -> collection(projects, projectId, "members", request));
        JsonNode tasks = join(tasksFuture);
        JsonNode members = join(membersFuture);
        Map<String, MutableWorkload> grouped = new TreeMap<>();
        for (JsonNode task : tasks) {
            String assigneeId = task.path("assigneeId").asText("");
            if (assigneeId.isBlank()) continue;
            MutableWorkload value = grouped.computeIfAbsent(assigneeId, ignored -> new MutableWorkload());
            value.tasks++;
            value.points += task.path("points").asInt(0);
            value.status.merge(normalize(task.path("status").asText("unknown")), 1, Integer::sum);
        }
        List<WorkloadRow> rows = new ArrayList<>();
        grouped.forEach((assigneeId, value) -> rows.add(
                new WorkloadRow(assigneeId, value.tasks, value.points, value.status)));
        WorkloadReadModel model = new WorkloadReadModel(members, rows);
        return response(model, cache.put(key, model) ? "MISS" : "BYPASS");
    }

    @GetMapping("/reports/{resource}")
    ResponseEntity<ApiResponse<ReportReadModel>> report(@PathVariable UUID projectId,
                                                         @PathVariable String resource,
                                                         @AuthenticationPrincipal Jwt jwt,
                                                         HttpServletRequest request) {
        String normalized = normalize(resource);
        if (!REPORT_RESOURCES.contains(normalized)) {
            throw new DownstreamException(HttpStatusCode.valueOf(400),
                    "{\"error\":{\"code\":\"unsupported_report\",\"message\":\"Supported reports: tasks, bugs, risks\","
                            + "\"fieldErrors\":{},\"traceId\":\"" + UUID.randomUUID() + "\"}}");
        }
        String key = cache.key(projectId, subject(jwt), "report-" + normalized);
        var cached = cache.get(key, ReportReadModel.class);
        if (cached.isPresent()) return response(cached.get(), "HIT");
        RestClient client = "risks".equals(normalized) ? operations : work;
        var itemsFuture = async(() -> collection(client, projectId, normalized, request));
        var membersFuture = async(() -> collection(projects, projectId, "members", request));
        JsonNode items = join(itemsFuture);
        JsonNode members = join(membersFuture);
        ReportReadModel model = new ReportReadModel(items, members,
                new ReportSummary(items.size(), counts(items, "status"), counts(items, "priority"),
                        counts(items, "level")));
        return response(model, cache.put(key, model) ? "MISS" : "BYPASS");
    }

    @GetMapping(value = "/reports/{resource}/export.csv", produces = "text/csv")
    ResponseEntity<String> exportCsv(@PathVariable UUID projectId, @PathVariable String resource,
                                     @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        ResponseEntity<ApiResponse<ReportReadModel>> report = report(projectId, resource, jwt, request);
        ReportReadModel model = report.getBody().data();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + normalize(resource) + "-report.csv")
                .header(ReadModelCache.HEADER, report.getHeaders().getFirst(ReadModelCache.HEADER))
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv(model.items()));
    }

    @ExceptionHandler(DownstreamException.class)
    ResponseEntity<String> downstream(DownstreamException exception) {
        return ResponseEntity.status(exception.status()).contentType(MediaType.APPLICATION_JSON)
                .body(exception.body());
    }

    private RestClient client(ObjectProvider<RestClient.Builder> clientBuilders, String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return clientBuilders.getIfAvailable(RestClient::builder).baseUrl(baseUrl).requestFactory(factory).build();
    }

    private CompletableFuture<JsonNode> async(Supplier<JsonNode> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private JsonNode join(CompletableFuture<JsonNode> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw exception;
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> response(T data, String status) {
        return ResponseEntity.ok().header(ReadModelCache.HEADER, status).body(ApiResponse.of(data));
    }

    private String subject(Jwt jwt) {
        String subject = jwt.getClaimAsString("uid");
        return subject == null || subject.isBlank() ? jwt.getSubject() : subject;
    }

    private JsonNode collection(RestClient client, UUID projectId, String resource, HttpServletRequest request) {
        JsonNode response = get(client, "/api/v1/projects/" + projectId + "/" + resource + "?size=200", request);
        return response.path("data");
    }

    private JsonNode get(RestClient client, String path, HttpServletRequest request) {
        try {
            JsonNode response = client.get().uri(path).headers(headers -> forward(request, headers))
                    .retrieve().body(JsonNode.class);
            if (response == null) {
                throw new DownstreamException(HttpStatusCode.valueOf(503),
                        "{\"error\":{\"code\":\"empty_downstream_response\","
                                + "\"message\":\"A downstream service returned no data\",\"fieldErrors\":{},"
                                + "\"traceId\":\"" + UUID.randomUUID() + "\"}}");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new DownstreamException(exception.getStatusCode(), exception.getResponseBodyAsString());
        }
    }

    private void forward(HttpServletRequest request, HttpHeaders headers) {
        copy(request, headers, HttpHeaders.AUTHORIZATION);
        copy(request, headers, HttpHeaders.COOKIE);
        copy(request, headers, "X-XSRF-TOKEN");
        copy(request, headers, "X-Request-Id");
    }

    private void copy(HttpServletRequest request, HttpHeaders headers, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) headers.set(name, value);
    }

    private Map<String, Integer> counts(JsonNode items, String field) {
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonNode item : items) {
            if (!item.hasNonNull(field)) continue;
            counts.merge(normalize(item.get(field).asText()), 1, Integer::sum);
        }
        return counts;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private String csv(JsonNode items) {
        StringBuilder output = new StringBuilder("id,title,status,priority,level,assigneeId,points,deadline\n");
        for (JsonNode item : items) {
            output.append(csv(item.path("id").asText())).append(',')
                    .append(csv(item.path("title").asText(item.path("name").asText()))).append(',')
                    .append(csv(item.path("status").asText())).append(',')
                    .append(csv(item.path("priority").asText())).append(',')
                    .append(csv(item.path("level").asText())).append(',')
                    .append(csv(item.path("assigneeId").asText())).append(',')
                    .append(csv(item.path("points").asText())).append(',')
                    .append(csv(item.path("deadline").asText())).append('\n');
        }
        return output.toString();
    }

    private String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public record DashboardReadModel(JsonNode meetings, JsonNode tasks, JsonNode taskColumns, JsonNode bugs,
                                     JsonNode risks, JsonNode team, DashboardSummary summary) {}
    public record DashboardSummary(int tasks, int bugs, int risks, int members, Map<String, Integer> taskStatus,
                                   Map<String, Integer> bugStatus, Map<String, Integer> riskStatus) {}
    public record WorkloadReadModel(JsonNode members, List<WorkloadRow> workload) {}
    public record WorkloadRow(String assigneeId, int tasks, int points, Map<String, Integer> status) {}
    public record ReportReadModel(JsonNode items, JsonNode members, ReportSummary summary) {}
    public record ReportSummary(int total, Map<String, Integer> status, Map<String, Integer> priority,
                                Map<String, Integer> level) {}

    private static final class MutableWorkload {
        int tasks;
        int points;
        final Map<String, Integer> status = new TreeMap<>();
    }

    private static final class DownstreamException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final HttpStatusCode status;
        private final String body;

        DownstreamException(HttpStatusCode status, String body) {
            super("Downstream request failed with status " + status.value());
            this.status = status;
            this.body = body;
        }

        HttpStatusCode status() { return status; }
        String body() { return body; }
    }
}
