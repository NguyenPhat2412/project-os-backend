package com.projectos.backend.organization.web;

import com.projectos.backend.organization.domain.AssetManagementApplicationService;
import com.projectos.backend.platform.api.ApiResponse;
import com.projectos.backend.platform.api.PageResponse;
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
@RequestMapping("/api/v1/organizations/{organizationId}")
public class AssetManagementController {
    private final AssetManagementApplicationService service;
    public AssetManagementController(AssetManagementApplicationService service) { this.service = service; }

    @GetMapping("/assets")
    PageResponse<AssetManagementApplicationService.AssetView> assets(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) { return service.assets(organizationId, page, size, actor(jwt), root(jwt)); }
    @PostMapping("/assets") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AssetManagementApplicationService.AssetView> createAsset(@PathVariable UUID organizationId, @RequestBody AssetManagementApplicationService.AssetInput input, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.createAsset(organizationId, input, actor(jwt), root(jwt))); }
    @PatchMapping("/assets/{assetId}")
    ApiResponse<AssetManagementApplicationService.AssetView> updateAsset(@PathVariable UUID organizationId, @PathVariable UUID assetId, @RequestBody AssetManagementApplicationService.AssetInput input, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateAsset(organizationId, assetId, input, actor(jwt), root(jwt))); }
    @DeleteMapping("/assets/{assetId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAsset(@PathVariable UUID organizationId, @PathVariable UUID assetId, @AuthenticationPrincipal Jwt jwt) { service.deleteAsset(organizationId, assetId, actor(jwt), root(jwt)); }

    @GetMapping("/resources")
    PageResponse<AssetManagementApplicationService.ResourceView> resources(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) { return service.resources(organizationId, page, size, actor(jwt), root(jwt)); }
    @PostMapping("/resources") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AssetManagementApplicationService.ResourceView> createResource(@PathVariable UUID organizationId, @RequestBody AssetManagementApplicationService.ResourceInput input, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.createResource(organizationId, input, actor(jwt), root(jwt))); }
    @PatchMapping("/resources/{resourceId}")
    ApiResponse<AssetManagementApplicationService.ResourceView> updateResource(@PathVariable UUID organizationId, @PathVariable UUID resourceId, @RequestBody AssetManagementApplicationService.ResourceInput input, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.updateResource(organizationId, resourceId, input, actor(jwt), root(jwt))); }
    @DeleteMapping("/resources/{resourceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteResource(@PathVariable UUID organizationId, @PathVariable UUID resourceId, @AuthenticationPrincipal Jwt jwt) { service.deleteResource(organizationId, resourceId, actor(jwt), root(jwt)); }

    @PostMapping("/assets/handovers") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AssetManagementApplicationService.HandoverView> createHandover(@PathVariable UUID organizationId, @RequestBody AssetManagementApplicationService.HandoverInput input, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.createHandover(organizationId, input, actor(jwt), root(jwt))); }
    @PostMapping("/assets/handovers/{handoverId}/confirm")
    ApiResponse<AssetManagementApplicationService.HandoverView> confirmHandover(@PathVariable UUID organizationId, @PathVariable UUID handoverId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.confirmHandover(organizationId, handoverId, actor(jwt), root(jwt))); }
    @PostMapping("/assets/handovers/{handoverId}/return")
    ApiResponse<AssetManagementApplicationService.HandoverView> returnHandover(@PathVariable UUID organizationId, @PathVariable UUID handoverId, @RequestBody ReturnInput input, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.returnHandover(organizationId, handoverId, input.conditionIn(), actor(jwt), root(jwt))); }
    @GetMapping("/employees/{employeeId}/assets")
    ApiResponse<List<AssetManagementApplicationService.AssetView>> employeeAssets(@PathVariable UUID organizationId, @PathVariable UUID employeeId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.employeeAssets(organizationId, employeeId, actor(jwt), root(jwt))); }
    @GetMapping("/assets/{assetId}/history")
    ApiResponse<List<AssetManagementApplicationService.HandoverHistoryView>> assetHistory(@PathVariable UUID organizationId, @PathVariable UUID assetId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.assetHistory(organizationId, assetId, actor(jwt), root(jwt))); }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }
    public record ReturnInput(String conditionIn) {}
}
