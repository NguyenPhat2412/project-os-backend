package com.projectos.backend.organization.web;

import com.projectos.backend.organization.domain.FeaturePositionProfileService;
import com.projectos.backend.platform.api.ApiResponse;
import com.projectos.backend.platform.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/feature-position-profiles")
public class FeaturePositionProfileController {
    private final FeaturePositionProfileService service;

    public FeaturePositionProfileController(FeaturePositionProfileService service) { this.service = service; }

    @GetMapping
    PageResponse<FeaturePositionProfileView> list(@PathVariable UUID organizationId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "100") int size,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return service.list(organizationId, page, size, actor(jwt), root(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<FeaturePositionProfileView> create(@PathVariable UUID organizationId,
                                                    @Valid @RequestBody FeaturePositionProfileRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.create(organizationId, request, actor(jwt), root(jwt)));
    }

    @PatchMapping("/{profileId}")
    ApiResponse<FeaturePositionProfileView> update(@PathVariable UUID organizationId, @PathVariable UUID profileId,
                                                    @Valid @RequestBody FeaturePositionProfilePatch request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.update(organizationId, profileId, request, actor(jwt), root(jwt)));
    }

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID organizationId, @PathVariable UUID profileId, @AuthenticationPrincipal Jwt jwt) {
        service.delete(organizationId, profileId, actor(jwt), root(jwt));
    }

    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private static boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }

    public record FeaturePositionProfileRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 80) String code,
            @Size(max = 150) String department,
            @Size(max = 4000) String description,
            @NotBlank @Pattern(regexp = "blue|red|yellow") String iconBg,
            @NotEmpty @Size(max = 300) List<@NotBlank @Size(max = 120) String> allowedFeatureKeys) {}

    public record FeaturePositionProfilePatch(
            @Size(max = 150) String name,
            @Size(max = 80) String code,
            @Size(max = 150) String department,
            @Size(max = 4000) String description,
            @Pattern(regexp = "blue|red|yellow") String iconBg,
            @Size(max = 300) List<@NotBlank @Size(max = 120) String> allowedFeatureKeys) {}

    public record FeaturePositionProfileView(UUID id, UUID organizationId, String name, String code, String department,
                                             String description, String iconBg, List<String> allowedFeatureKeys,
                                             boolean isCustom, Instant createdAt, Instant updatedAt) {}
}
