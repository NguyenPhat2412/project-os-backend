package vn.uytinmang.projectos.identity.user;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import vn.uytinmang.projectos.platform.api.ApiException;

@Component
class ProjectDirectoryClient {
    private final RestClient projects;

    ProjectDirectoryClient(@Value("${app.project-service-url:http://localhost:8082}") String projectUrl,
                           ObjectProvider<RestClient.Builder> builders) {
        this.projects = builders.getIfAvailable(RestClient::builder).baseUrl(projectUrl).build();
    }

    Set<UUID> memberIds(UUID projectId, Jwt jwt) {
        try {
            Set<UUID> result = new HashSet<>();
            int page = 0;
            int totalPages;
            do {
                int currentPage = page;
                JsonNode response = projects.get().uri(builder -> builder
                                .path("/api/v1/projects/{projectId}/members")
                                .queryParam("page", currentPage).queryParam("size", 200).build(projectId))
                        .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                        .retrieve().body(JsonNode.class);
                if (response == null || !response.path("data").isArray()) return Set.of();
                response.path("data").valueStream()
                        .map(ProjectDirectoryClient::memberId)
                        .filter(value -> value != null && !value.isBlank())
                        .map(UUID::fromString)
                        .forEach(result::add);
                totalPages = Math.max(1, response.path("meta").path("totalPages").asInt(1));
                page++;
            } while (page < totalPages);
            return Set.copyOf(result);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 403) {
                throw new ApiException(HttpStatus.FORBIDDEN, "project_access_denied",
                        "You cannot view this project's directory");
            }
            if (exception.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "project_not_found", "Project not found");
            }
            throw unavailable();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "invalid_project_member",
                    "Project service returned an invalid member identifier");
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static String memberId(JsonNode member) {
        for (String field : new String[]{"uid", "memberId", "userId"}) {
            String value = member.path(field).asText(null);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "project_service_unavailable",
                "Project service is unavailable");
    }
}
