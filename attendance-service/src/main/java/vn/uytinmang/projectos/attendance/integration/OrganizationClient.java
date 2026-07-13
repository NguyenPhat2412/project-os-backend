package vn.uytinmang.projectos.attendance.integration;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.ApiResponse;

@Component
public class OrganizationClient {
    private final RestClient client;
    OrganizationClient(@Value("${app.organization-service-url}") String baseUrl,
                       @Value("${app.internal-token}") String token) {
        client = RestClient.builder().baseUrl(baseUrl).defaultHeader("X-Internal-Token", token).build();
    }
    public Employee employeeByUser(UUID organizationId, UUID userId) { return get("/api/v1/internal/organizations/{organizationId}/employees/by-user/{userId}", organizationId, userId, new ParameterizedTypeReference<ApiResponse<Employee>>() {}); }
    public Employee employee(UUID organizationId, UUID employeeId) { return get("/api/v1/internal/organizations/{organizationId}/employees/{employeeId}", organizationId, employeeId, new ParameterizedTypeReference<ApiResponse<Employee>>() {}); }
    public Access access(UUID organizationId, UUID userId) { return get("/api/v1/internal/organizations/{organizationId}/access/{userId}", organizationId, userId, new ParameterizedTypeReference<ApiResponse<Access>>() {}); }
    public String timezone(UUID organizationId) {
        try { ApiResponse<String> response = client.get().uri("/api/v1/internal/organizations/{organizationId}/timezone", organizationId).retrieve().body(new ParameterizedTypeReference<>() {}); return response == null ? null : response.data(); }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) throw new ApiException(HttpStatus.NOT_FOUND, "organization_not_found", "Organization not found");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "organization_unavailable", "Organization service is unavailable");
        }
    }
    private <T> T get(String path, UUID organizationId, UUID subjectId, ParameterizedTypeReference<ApiResponse<T>> type) {
        try { ApiResponse<T> response = client.get().uri(path, organizationId, subjectId).retrieve().body(type); return response == null ? null : response.data(); }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) throw new ApiException(HttpStatus.NOT_FOUND, "organization_subject_not_found", "Organization employee not found");
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN) throw new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "organization_unavailable", "Organization service is unavailable");
        }
    }
    public record Employee(UUID id, UUID organizationId, UUID supervisorId, UUID userId, String status) {}
    public record Access(String timezone, String role) {}
}
