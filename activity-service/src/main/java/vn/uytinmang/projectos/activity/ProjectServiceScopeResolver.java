package vn.uytinmang.projectos.activity;

import tools.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.uytinmang.projectos.platform.api.ApiException;

@Component
@ConditionalOnMissingBean(ProjectScopeResolver.class)
class ProjectServiceScopeResolver implements ProjectScopeResolver {
    private final RestClient projects;
    private final String internalToken;

    ProjectServiceScopeResolver(@Value("${app.rbac.project-service-url:http://localhost:8082}") String projectServiceUrl,
                                @Value("${app.rbac.internal-token}") String internalToken,
                                ObjectProvider<RestClient.Builder> clientBuilders) {
        this.projects = clientBuilders.getIfAvailable(RestClient::builder).baseUrl(projectServiceUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public Optional<UUID> organizationId(UUID projectId) {
        try {
            JsonNode response = projects.get().uri("/api/v1/internal/projects/{projectId}/scope", projectId)
                    .header("X-Internal-Token", internalToken).retrieve().body(JsonNode.class);
            String organizationId = response == null ? null : response.path("data").path("organizationId").asText(null);
            return organizationId == null || organizationId.isBlank()
                    ? Optional.empty() : Optional.of(UUID.fromString(organizationId));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) return Optional.empty();
            throw unavailable(exception);
        } catch (IllegalArgumentException exception) {
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private ApiException unavailable(Exception cause) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "project_scope_unavailable",
                "Project scope service is unavailable");
    }
}
