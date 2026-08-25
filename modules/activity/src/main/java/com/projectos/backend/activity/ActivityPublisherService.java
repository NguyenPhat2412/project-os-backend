package com.projectos.backend.activity;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Application-facing publisher used by the modular monolith adapter. */
@Service
public class ActivityPublisherService {
    private final ScopedActivityService activities;

    public ActivityPublisherService(ScopedActivityService activities) {
        this.activities = activities;
    }

    public void publish(JsonNode body) {
        try {
            UUID eventId = UUID.fromString(body.path("eventId").asText());
            UUID projectId = UUID.fromString(body.path("projectId").asText());
            UUID actorId = UUID.fromString(body.path("actorId").asText());
            Instant occurredAt = body.path("occurredAt").asText(null) == null
                    ? Instant.now() : Instant.parse(body.path("occurredAt").asText());
            activities.record(new ScopedActivityService.ActivityCommand(eventId, projectId, actorId,
                    body.path("resource").asText(null), body.path("resourceId").asText(null),
                    body.path("action").asText(null), occurredAt, body.get("snapshot")));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid activity payload", exception);
        }
    }
}
