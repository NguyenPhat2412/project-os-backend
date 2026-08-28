package com.projectos.backend.organization.web;

import com.projectos.backend.organization.EnvironmentConfigVersionService;
import com.projectos.backend.organization.domain.EnvironmentConfigVersion;
import com.projectos.backend.platform.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1/admin/environment-config")
public class AdminEnvironmentConfigController {
    private final EnvironmentConfigVersionService service;
    private final boolean readOnly;
    private final String activeProfiles;

    public AdminEnvironmentConfigController(EnvironmentConfigVersionService service,
                                            @Value("${app.environment-config.read-only:false}") boolean readOnly,
                                            @Value("${spring.profiles.active:local}") String activeProfiles) {
        this.service = service;
        this.readOnly = readOnly;
        this.activeProfiles = activeProfiles;
    }

    @GetMapping
    ApiResponse<Map<String, String>> current(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.snapshot(root(jwt)));
    }

    @GetMapping("/capabilities")
    ApiResponse<EnvironmentConfigCapabilities> capabilities(@AuthenticationPrincipal Jwt jwt) {
        root(jwt);
        return ApiResponse.of(new EnvironmentConfigCapabilities(environmentName(), readOnly,
                service.isFileConfigured() ? "environment-file" : "process"));
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    ApiResponse<EnvironmentConfigVersionView> apply(@Valid @RequestBody EnvironmentConfigUpdate request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(EnvironmentConfigVersionView.from(
                service.apply(root(jwt), actor(jwt), request.values())));
    }

    @GetMapping("/versions")
    ApiResponse<List<EnvironmentConfigVersionView>> versions(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.list(root(jwt)).stream().map(EnvironmentConfigVersionView::from).toList());
    }

    @PostMapping("/rollback")
    ApiResponse<EnvironmentConfigVersionView> rollback(@Valid @RequestBody EnvironmentConfigRollback request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(EnvironmentConfigVersionView.from(
                service.rollback(root(jwt), actor(jwt), request.versionId())));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }

    private boolean root(Jwt jwt) {
        return "ROOT_ADMIN".equals(jwt.getClaimAsString("role"));
    }

    private String environmentName() {
        String profiles = activeProfiles == null ? "" : activeProfiles.toLowerCase(java.util.Locale.ROOT);
        if (profiles.contains("production")) return "production";
        if (profiles.contains("staging")) return "staging";
        return "local";
    }

    public record EnvironmentConfigUpdate(@NotNull Map<String, String> values) { }
    public record EnvironmentConfigRollback(@NotNull UUID versionId) { }

    public record EnvironmentConfigCapabilities(String environment, boolean readOnly, String source) { }

    public record EnvironmentConfigVersionView(UUID id, String status, String checksum, String changedKeys,
                                               boolean reloadRequired, UUID createdBy, Instant createdAt, String notes) {
        static EnvironmentConfigVersionView from(EnvironmentConfigVersion value) {
            return new EnvironmentConfigVersionView(value.getId(), value.getStatus(), value.getChecksum(),
                    value.getChangedKeys(), value.isReloadRequired(), value.getCreatedBy(), value.getCreatedAt(), value.getNotes());
        }
    }
}
