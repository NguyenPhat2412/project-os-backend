package vn.uytinmang.projectos.activity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/internal/activities")
class InternalActivityController {
    private final ScopedActivityService activities;
    private final byte[] internalToken;

    InternalActivityController(ScopedActivityService activities,
                               @Value("${app.outbox.internal-token}") String internalToken) {
        this.activities = activities;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<ScopedActivityService.ActivityWriteResult> create(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ObjectNode body) {
        byte[] candidate = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalToken, candidate)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "invalid_internal_token", "Invalid internal token");
        }
        UUID projectId = uuid(body, "projectId");
        UUID actorId = uuid(body, "actorId");
        UUID eventId = uuid(body, "eventId");
        Instant occurredAt = instant(body.path("occurredAt").asText(null));
        JsonNode snapshot = body.get("snapshot");
        return ApiResponse.of(activities.record(new ScopedActivityService.ActivityCommand(eventId, projectId,
                actorId, body.path("resource").asText(null), body.path("resourceId").asText(null),
                body.path("action").asText(null), occurredAt, snapshot)));
    }

    private UUID uuid(ObjectNode body, String field) {
        try {
            return UUID.fromString(body.path(field).asText());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_activity", field + " must be a UUID");
        }
    }

    private Instant instant(String value) {
        if (value == null || value.isBlank()) return Instant.now();
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_activity", "occurredAt must be ISO-8601");
        }
    }
}
