package vn.uytinmang.projectos.resource;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository events;
    private final ObjectMapper mapper;
    private final boolean enabled;

    OutboxPublisher(OutboxEventRepository events, ObjectMapper mapper,
                    @Value("${app.outbox.enabled:true}") boolean enabled) {
        this.events = events;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    void record(ResourceRecord record, String action, UUID actorId) {
        if (!enabled) return;
        String externalId = record.getLegacyId() == null ? record.getId().toString() : record.getLegacyId();
        record(record.getProjectId(), record.getResourceType(), externalId, action,
                record.getPayload(), actorId);
    }

    public void record(UUID projectId, String resource, String resourceId, String action,
                       tools.jackson.databind.JsonNode snapshot, UUID actorId) {
        if (!enabled) return;
        String eventType = resource + "." + action;
        ObjectNode payload = mapper.createObjectNode();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("projectId", projectId.toString());
        payload.put("actorId", actorId.toString());
        payload.put("resource", resource);
        payload.put("resourceId", resourceId);
        payload.put("action", action);
        payload.put("occurredAt", Instant.now().toString());
        payload.set("snapshot", snapshot.deepCopy());
        events.save(new OutboxEvent(eventType, payload));
    }
}
