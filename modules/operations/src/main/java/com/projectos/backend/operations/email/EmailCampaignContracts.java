package com.projectos.backend.operations.email;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EmailCampaignContracts {
    private EmailCampaignContracts() {}
    public record PreviewRequest(String subject, String bodyHtml, UUID templateId,
                                 List<UUID> employeeIds, UUID departmentId) {}
    public record CampaignRequest(String subject, String bodyHtml, UUID templateId,
                                  List<UUID> employeeIds, UUID departmentId,
                                  String previewHash, String idempotencyKey) {}
    public record TemplateRequest(String code, String title, String subject, String bodyHtml,
                                  List<String> allowedVariables, String status) {}
    public record RecipientPreview(UUID employeeId, String employeeName, String employeeCode,
                                   String email, String reason) {}
    public record PreviewResponse(List<RecipientPreview> validRecipients,
                                  List<RecipientPreview> excludedRecipients, String snapshotHash) {}
    public record TemplateView(UUID id, UUID organizationId, String code, String title,
                               String subject, String bodyHtml, List<String> allowedVariables,
                               String status, Instant updatedAt) {}
    public record CampaignView(UUID id, UUID organizationId, String subject, UUID templateId,
                               String status, int totalRecipients, int sentCount, int failedCount,
                               String previewHash, String idempotencyKey, Instant createdAt,
                               Instant queuedAt, Instant completedAt) {}
}
