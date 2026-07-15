package vn.uytinmang.projectos.project.application;

import tools.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.uytinmang.projectos.platform.api.ApiException;

/** Verifies the organization boundary before a project is created or accessed. */
@Component
class OrganizationAccessClient {
    private static final Set<String> PROJECT_MANAGERS = Set.of("OWNER", "ADMIN", "DEPARTMENT_MANAGER");

    private final boolean enabled;
    private final RestClient organizations;
    private final String token;

    OrganizationAccessClient(@Value("${app.rbac.enabled:true}") boolean enabled,
                             @Value("${app.rbac.organization-service-url:http://localhost:8087}") String organizationUrl,
                             @Value("${app.rbac.internal-token}") String token,
                             ObjectProvider<RestClient.Builder> builders) {
        this.enabled = enabled;
        this.organizations = builders.getIfAvailable(RestClient::builder).baseUrl(organizationUrl).build();
        this.token = token;
    }

    void requireMember(UUID organizationId, UUID actorId, boolean root) {
        if (root || !enabled) return;
        access(organizationId, actorId);
    }

    void requireProjectManager(UUID organizationId, UUID actorId, boolean root) {
        if (root || !enabled) return;
        String role = access(organizationId, actorId);
        if (!PROJECT_MANAGERS.contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "organization_project_manager_required",
                    "Organization manager access is required to create a project");
        }
    }

    private String access(UUID organizationId, UUID actorId) {
        try {
            JsonNode response = organizations.get()
                    .uri("/api/v1/internal/organizations/{organizationId}/access/{userId}", organizationId, actorId)
                    .header("X-Internal-Token", token)
                    .retrieve()
                    .body(JsonNode.class);
            String role = response == null ? null : response.path("data").path("role").asText(null);
            if (role == null || role.isBlank()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied");
            }
            return role.trim().toUpperCase(Locale.ROOT);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_service_unavailable",
                    "Organization service is unavailable");
        }
    }
}
