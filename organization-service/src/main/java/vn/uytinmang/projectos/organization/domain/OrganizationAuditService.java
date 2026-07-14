package vn.uytinmang.projectos.organization.domain;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.PageResponse;

@Service
public class OrganizationAuditService {
    private final OrganizationAuditLogRepository logs;
    private final OrganizationMembershipRepository memberships;
    private final ObjectMapper json;

    OrganizationAuditService(OrganizationAuditLogRepository logs, OrganizationMembershipRepository memberships,
                             ObjectMapper json) {
        this.logs = logs;
        this.memberships = memberships;
        this.json = json;
    }

    void record(UUID organizationId, UUID actorId, String eventType, String entityType, UUID entityId,
                Object beforeState, Object afterState, String reason) {
        logs.save(new OrganizationAuditLog(organizationId, actorId, eventType, entityType, entityId,
                tree(beforeState), tree(afterState), reason));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> list(UUID organizationId, int page, int size, UUID actorId, boolean root) {
        requireAdmin(organizationId, actorId, root);
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "page must be >= 0 and size 1-100");
        }
        var result = logs.findByOrganizationId(organizationId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).map(AuditView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    private JsonNode tree(Object value) { return value == null ? null : json.valueToTree(value); }

    private void requireAdmin(UUID organizationId, UUID actorId, boolean root) {
        if (root) return;
        var membership = memberships.findByOrganizationIdAndUserId(organizationId, actorId)
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied",
                        "Organization access denied"));
        if (membership.getRole() != OrganizationMembership.Role.OWNER
                && membership.getRole() != OrganizationMembership.Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "organization_admin_required",
                    "Organization admin access is required");
        }
    }

    public record AuditView(UUID id, UUID organizationId, UUID actorId, String eventType, String entityType,
                            UUID entityId, JsonNode beforeState, JsonNode afterState, String reason,
                            java.time.Instant createdAt) {
        static AuditView from(OrganizationAuditLog value) {
            return new AuditView(value.getId(), value.getOrganizationId(), value.getActorId(), value.getEventType(),
                    value.getEntityType(), value.getEntityId(), value.getBeforeState(), value.getAfterState(),
                    value.getReason(), value.getCreatedAt());
        }
    }
}
