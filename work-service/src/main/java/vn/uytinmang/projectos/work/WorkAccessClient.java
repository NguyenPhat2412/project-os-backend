package vn.uytinmang.projectos.work;

import tools.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.uytinmang.projectos.platform.api.ApiException;

@Component
class WorkAccessClient {
    private final boolean enabled;
    private final RestClient projects;
    private final RestClient organizations;
    private final String token;

    WorkAccessClient(@Value("${app.rbac.enabled:true}") boolean enabled,
                     @Value("${app.rbac.project-service-url:http://localhost:8082}") String projectUrl,
                     @Value("${app.rbac.organization-service-url:http://localhost:8087}") String organizationUrl,
                     @Value("${app.rbac.internal-token}") String token,
                     ObjectProvider<RestClient.Builder> builders) {
        this.enabled = enabled;
        this.projects = builders.getIfAvailable(RestClient::builder).baseUrl(projectUrl).build();
        this.organizations = builders.getIfAvailable(RestClient::builder).baseUrl(organizationUrl).build();
        this.token = token;
    }

    void requireProject(UUID projectId, UUID actorId, String resource, String action, boolean root) {
        if (root || !enabled) return;
        try {
            JsonNode response = projects.get().uri(builder -> builder
                            .path("/api/v1/internal/projects/{projectId}/permissions/check")
                            .queryParam("actorId", actorId).queryParam("resource", resource)
                            .queryParam("action", action).build(projectId))
                    .header("X-Internal-Token", token).retrieve().body(JsonNode.class);
            if (response == null || !response.path("data").path("allowed").asBoolean(false)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "permission_denied", "Project access denied");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "permission_service_unavailable",
                    "Permission service is unavailable");
        }
    }

    Set<UUID> directReportUsers(UUID organizationId, UUID managerUserId, boolean root) {
        if (root || !enabled) return Set.of();
        try {
            JsonNode employee = organizations.get()
                    .uri("/api/v1/internal/organizations/{organizationId}/employees/by-user/{userId}",
                            organizationId, managerUserId)
                    .header("X-Internal-Token", token).retrieve().body(JsonNode.class);
            String employeeId = employee == null ? null : employee.path("data").path("id").asText(null);
            if (employeeId == null) return Set.of();
            JsonNode reports = organizations.get()
                    .uri("/api/v1/internal/organizations/{organizationId}/employees/by-supervisor/{employeeId}",
                            organizationId, employeeId)
                    .header("X-Internal-Token", token).retrieve().body(JsonNode.class);
            if (reports == null || !reports.path("data").isArray()) return Set.of();
            return reports.path("data").valueStream().map(value -> value.path("userId").asText(null))
                    .filter(value -> value != null && !value.isBlank()).map(UUID::fromString)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_service_unavailable",
                    "Organization service is unavailable");
        }
    }

    ReportRecipient reportRecipient(UUID organizationId, UUID actorId, boolean root) {
        if (root || !enabled) return null;
        try {
            JsonNode employee = organizations.get()
                    .uri("/api/v1/internal/organizations/{organizationId}/employees/by-user/{userId}",
                            organizationId, actorId)
                    .header("X-Internal-Token", token).retrieve().body(JsonNode.class);
            String supervisorId = employee == null ? null : employee.path("data").path("supervisorId").asText(null);
            if (supervisorId == null || supervisorId.isBlank()) return null;

            JsonNode supervisor = organizations.get()
                    .uri("/api/v1/internal/organizations/{organizationId}/employees/{employeeId}",
                            organizationId, UUID.fromString(supervisorId))
                    .header("X-Internal-Token", token).retrieve().body(JsonNode.class);
            JsonNode data = supervisor == null ? null : supervisor.path("data");
            String userId = data == null ? null : data.path("userId").asText(null);
            if (userId == null || userId.isBlank()) return null;
            return new ReportRecipient(UUID.fromString(userId), data.path("fullName").asText("Quản lý trực tiếp"),
                    blankToNull(data.path("title").asText(null)));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) return null;
            throw organizationUnavailable();
        } catch (IllegalArgumentException exception) {
            return null;
        } catch (Exception exception) {
            throw organizationUnavailable();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ApiException organizationUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_service_unavailable",
                "Organization service is unavailable");
    }

    record ReportRecipient(UUID userId, String fullName, String title) {}
}
