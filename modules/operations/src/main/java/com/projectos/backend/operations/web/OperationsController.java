package com.projectos.backend.operations.web;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.projectos.backend.operations.application.OperationsApplicationService;
import com.projectos.backend.operations.web.OperationsMutationRequest;
import com.projectos.backend.platform.api.PageResponse;
import com.projectos.backend.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
public class OperationsController {
    private final OperationsApplicationService service;

    public OperationsController(OperationsApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{resource}")
    PageResponse<OperationsResourceDto> list(@PathVariable UUID organizationId,
                                           @PathVariable String resource,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "100") int size,
                                           @RequestParam(defaultValue = "") String search,
                                           @RequestParam(defaultValue = "") String category,
                                           @AuthenticationPrincipal Jwt jwt) {
        return listResource(organizationId, resource, page, size, search, category, jwt);
    }

    @GetMapping("/leave/balances")
    PageResponse<OperationsResourceDto> listLeaveBalances(@PathVariable UUID organizationId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "100") int size,
                                                        @RequestParam(defaultValue = "") String search,
                                                        @AuthenticationPrincipal Jwt jwt) {
        return listResource(organizationId, "leave-balances", page, size, search, "", jwt);
    }

    @GetMapping("/contract-warnings")
    PageResponse<OperationsResourceDto> listContractWarnings(@PathVariable UUID organizationId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "100") int size,
                                                             @RequestParam(defaultValue = "") String search,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return listResource(organizationId, "contract-warnings", page, size, search, "", jwt);
    }

    @PostMapping("/contract-warnings/{id}/remind")
    ApiResponse<ContractWarningReminderDto> remindContractWarning(@PathVariable UUID organizationId,
                                                                  @PathVariable String id,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.queueContractWarningReminderDto(organizationId, id, actor(jwt), root(jwt)));
    }

    @PostMapping("/{resource}") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<OperationsResourceDto> create(@PathVariable UUID organizationId, @PathVariable String resource, @RequestBody OperationsMutationRequest payload, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.createDto(organizationId, resource, payload, actor(jwt), root(jwt)));
    }

    @PatchMapping("/{resource}/{id}")
    ApiResponse<OperationsResourceDto> update(@PathVariable UUID organizationId, @PathVariable String resource, @PathVariable String id, @RequestBody OperationsMutationRequest payload, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.updateDto(organizationId, resource, id, payload, actor(jwt), root(jwt)));
    }

    @DeleteMapping("/{resource}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID organizationId, @PathVariable String resource, @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(organizationId, resource, id, actor(jwt), root(jwt));
    }

    private PageResponse<OperationsResourceDto> listResource(UUID organizationId, String resource, int page, int size,
                                                           String search, String category, Jwt jwt) {
        return service.listDto(organizationId, resource, page, size, search, category, actor(jwt), root(jwt));
    }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }
}
