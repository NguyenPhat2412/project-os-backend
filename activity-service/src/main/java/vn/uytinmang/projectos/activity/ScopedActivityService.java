package vn.uytinmang.projectos.activity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.PageResponse;

@Service
class ScopedActivityService {
    private final ActivityEventRepository events;
    private final ProjectScopeResolver projectScope;

    ScopedActivityService(ActivityEventRepository events, ProjectScopeResolver projectScope) {
        this.events = events;
        this.projectScope = projectScope;
    }

    @Transactional
    ActivityWriteResult record(ActivityCommand command) {
        Optional<UUID> organizationId = projectScope.organizationId(command.projectId());
        if (organizationId.isEmpty()) return ActivityWriteResult.ignored(command.eventId());
        Optional<ActivityEvent> existing = events.findByEventId(command.eventId());
        if (existing.isPresent()) return ActivityWriteResult.recorded(existing.get());

        ActivityEvent event = new ActivityEvent(command.eventId(), organizationId.get(), command.projectId(),
                command.actorId(), normalize(command.resource(), "activity"), normalize(command.action(), "updated"),
                subject(command.resource(), command.resourceId(), command.snapshot()), command.occurredAt());
        return ActivityWriteResult.recorded(events.save(event));
    }

    @Transactional(readOnly = true)
    PageResponse<ActivityView> listForActor(UUID projectId, UUID actorId, int page, int size) {
        validatePage(page, size);
        Optional<UUID> organizationId = projectScope.organizationId(projectId);
        if (organizationId.isEmpty()) return PageResponse.of(java.util.List.of(), page, size, 0, 0);
        var result = events.findByOrganizationIdAndProjectIdAndActorIdOrderByOccurredAtDesc(
                organizationId.get(), projectId, actorId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return PageResponse.of(result.getContent().stream().map(ActivityView::from).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    ActivityView getForActor(UUID projectId, UUID actorId, UUID eventId) {
        UUID organizationId = projectScope.organizationId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "activity_not_found", "Activity not found"));
        ActivityEvent event = events.findByIdAndOrganizationIdAndProjectIdAndActorId(eventId, organizationId, projectId, actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "activity_not_found", "Activity not found"));
        return ActivityView.from(event);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "page must be >= 0 and size must be between 1 and 100");
        }
    }

    private String subject(String resource, String resourceId, JsonNode snapshot) {
        if (snapshot != null && snapshot.isObject()) {
            for (String field : new String[] {"title", "name", "key", "displayName", "fullName"}) {
                String value = text(snapshot, field);
                if (value != null && !isUuid(value)) return limit(value);
            }
        }
        if (resourceId != null && !isUuid(resourceId) && resourceId.matches("[A-Za-z]+-[0-9]+")) {
            return resourceId;
        }
        return resourceLabel(resource);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }

    private String limit(String value) { return value.length() <= 255 ? value : value.substring(0, 255); }

    private String resourceLabel(String resource) {
        return switch (normalize(resource, "activity")) {
            case "tasks" -> "Công việc";
            case "bugs" -> "Lỗi";
            case "sprints" -> "Sprint";
            case "meetings" -> "Cuộc họp";
            case "members" -> "Thành viên dự án";
            case "projects" -> "Dự án";
            default -> "Hoạt động";
        };
    }

    record ActivityCommand(UUID eventId, UUID projectId, UUID actorId, String resource, String resourceId,
                           String action, Instant occurredAt, JsonNode snapshot) {
    }

    record ActivityWriteResult(UUID id, UUID eventId, boolean recorded) {
        static ActivityWriteResult recorded(ActivityEvent event) {
            return new ActivityWriteResult(event.getId(), event.getEventId(), true);
        }

        static ActivityWriteResult ignored(UUID eventId) { return new ActivityWriteResult(null, eventId, false); }
    }

    record ActivityView(UUID id, String resource, String action, String subject, Instant occurredAt) {
        static ActivityView from(ActivityEvent event) {
            return new ActivityView(event.getId(), event.getResource(), event.getAction(), event.getSubject(),
                    event.getOccurredAt());
        }
    }
}
