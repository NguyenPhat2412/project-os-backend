package com.projectos.backend.organization.web;

import com.projectos.backend.organization.onboarding.OnboardingService;
import com.projectos.backend.platform.api.ApiResponse;
import com.projectos.backend.platform.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController {
    private final OnboardingService service;
    public OnboardingController(OnboardingService service) { this.service = service; }

    @PostMapping("/api/v1/organizations/{organizationId}/onboarding/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<InvitationCreated> createInvitation(@PathVariable UUID organizationId, @Valid @RequestBody InvitationRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.createInvitation(organizationId, request, actor(jwt), root(jwt)));
    }

    @GetMapping("/api/v1/public/onboarding")
    ApiResponse<InvitationView> invitation(@RequestParam String token) { return ApiResponse.of(service.publicInvitation(token)); }

    @PostMapping("/api/v1/public/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SubmissionResult> submit(@RequestParam String token, @Valid @RequestBody SubmissionRequest request) {
        return ApiResponse.of(service.submit(token, request));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/onboarding/requests")
    PageResponse<RequestView> requests(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        return service.requests(organizationId, page, size, actor(jwt), root(jwt));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/onboarding/requests/summary")
    ApiResponse<RequestSummary> summary(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.summary(organizationId, actor(jwt), root(jwt)));
    }

    @PostMapping("/api/v1/organizations/{organizationId}/onboarding/requests/{requestId}/approve")
    ApiResponse<RequestView> approve(@PathVariable UUID organizationId, @PathVariable UUID requestId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.approve(organizationId, requestId, actor(jwt), root(jwt)));
    }

    @PostMapping("/api/v1/organizations/{organizationId}/onboarding/requests/{requestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@PathVariable UUID organizationId, @PathVariable UUID requestId, @RequestBody RejectRequest request, @AuthenticationPrincipal Jwt jwt) {
        service.reject(organizationId, requestId, request == null ? null : request.reason(), actor(jwt), root(jwt));
    }

    @DeleteMapping("/api/v1/organizations/{organizationId}/onboarding/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID organizationId, @PathVariable UUID invitationId, @AuthenticationPrincipal Jwt jwt) {
        service.revoke(organizationId, invitationId, actor(jwt), root(jwt));
    }

    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private static boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }

    public record InvitationRequest(UUID departmentId, UUID positionId, @Size(max = 254) @Email String inviteEmail,
                                    String targetRole, Integer expiresInHours) {}
    public record InvitationCreated(UUID id, String inviteUrl, Instant expiresAt, String status) {}
    public record InvitationView(UUID id, String status, Instant expiresAt, String inviteEmail, String targetRole,
                                 String departmentName, String positionTitle) {}
    public record SubmissionRequest(@NotBlank @Size(max = 255) String fullName, @NotBlank String birthDate,
                                     String gender, @NotBlank @Size(max = 50) String citizenId, String idIssueDate,
                                     String idIssuePlace, String birthPlace, String maritalStatus, String ethnicity,
                                     String religion, @NotBlank String phone, @NotBlank @Email String email,
                                     String permanentAddress, String currentAddress, String bankName,
                                     String bankAccountNumber, String bankAccountHolder, String taxCode,
                                     String socialInsuranceNumber, String emergencyContactName,
                                     String emergencyContactRelationship, String emergencyContactPhone,
                                     String educationLevel, String majorField, String personalNotes,
                                     @NotNull Boolean consent) {}
    public record SubmissionResult(String referenceCode, String status, String message) {}
    public record RequestSummary(long pendingCount) {}
    public record RejectRequest(String reason) {}
    public record RequestView(UUID id, String referenceCode, String status, String fullName, String email,
                               String phone, String birthDate, String citizenId, Instant submittedAt,
                               String rejectionReason, UUID employeeId, String departmentName, String positionTitle) {}
}
