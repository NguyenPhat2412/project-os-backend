package vn.uytinmang.projectos.resource;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.uytinmang.projectos.platform.api.ApiSecurityErrorHandler;

@Component
@ConditionalOnProperty(name = "app.rbac.enabled", havingValue = "true")
public class ProjectPermissionFilter extends OncePerRequestFilter {
    private static final Set<String> UNCHECKED_METHODS = Set.of("HEAD", "OPTIONS");
    private final RestClient projects;
    private final String internalToken;
    private final ApiSecurityErrorHandler errors;

    ProjectPermissionFilter(@Value("${app.rbac.project-service-url:http://localhost:8082}") String projectServiceUrl,
                            @Value("${app.rbac.internal-token}") String internalToken,
                            ObjectProvider<RestClient.Builder> clientBuilders, ApiSecurityErrorHandler errors) {
        this.projects = clientBuilders.getIfAvailable(RestClient::builder).baseUrl(projectServiceUrl).build();
        this.internalToken = internalToken;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (UNCHECKED_METHODS.contains(request.getMethod())
                || request.getRequestURI().startsWith("/api/v1/internal/")) {
            chain.doFilter(request, response);
            return;
        }
        PathScope scope = scope(request.getRequestURI());
        if (scope == null || !(SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken authentication)) {
            chain.doFilter(request, response);
            return;
        }
        if (authentication.getAuthorities().stream().anyMatch(role -> "ROLE_ROOT_ADMIN".equals(role.getAuthority()))) {
            chain.doFilter(request, response);
            return;
        }
        boolean allowed;
        try {
            JsonNode result = projects.get()
                    .uri(builder -> builder.path("/api/v1/internal/projects/{projectId}/permissions/check")
                            .queryParam("actorId", authentication.getToken().getClaimAsString("uid"))
                            .queryParam("resource", scope.resource())
                            .queryParam("action", action(request)).build(scope.projectId()))
                    .header("X-Internal-Token", internalToken)
                    .retrieve().body(JsonNode.class);
            allowed = result != null && result.path("data").path("allowed").asBoolean(false);
        } catch (Exception exception) {
            error(request, response, HttpStatus.SERVICE_UNAVAILABLE, "permission_service_unavailable",
                    "Permission service is unavailable");
            return;
        }
        if (!allowed) {
            error(request, response, HttpStatus.FORBIDDEN, "permission_denied", "You do not have permission for this action");
            return;
        }
        chain.doFilter(request, response);
    }

    private PathScope scope(String path) {
        String[] segments = path.split("/");
        if (segments.length < 5 || !"api".equals(segments[1]) || !"v1".equals(segments[2])
                || !"projects".equals(segments[3])) return null;
        try {
            UUID projectId = UUID.fromString(segments[4]);
            String resource = segments.length > 5 && !segments[5].isBlank() ? segments[5] : "projects";
            if ("tasks".equals(resource)) resource = "tasks-all";
            return new PathScope(projectId, resource.replace('_', '-'));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String action(HttpServletRequest request) {
        if (request.getRequestURI().endsWith("/reorder")) return "update";
        return switch (request.getMethod()) {
            case "POST" -> "create";
            case "PUT", "PATCH" -> "update";
            case "DELETE" -> "delete";
            default -> "read";
        };
    }

    private void error(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                       String code, String message) throws IOException {
        errors.write(request, response, status, code, message);
    }

    private record PathScope(UUID projectId, String resource) {
    }
}
