package vn.uytinmang.projectos.resource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.PageResponse;

@Service
public class ResourceApplicationService {
    private final ResourceRecordRepository records;
    private final ResourceCatalog catalog;
    private final OutboxPublisher outbox;

    public ResourceApplicationService(ResourceRecordRepository records, ResourceCatalog catalog,
                                      OutboxPublisher outbox) {
        this.records = records;
        this.catalog = catalog;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public PageResponse<JsonNode> list(UUID projectId, String resource, int page, int size) {
        catalog.require(resource);
        if (page < 0 || size < 1 || size > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "page must be >= 0 and size must be between 1 and 200");
        }
        var result = records.findAllByProjectIdAndResourceType(projectId, resource,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
        return PageResponse.of(result.getContent().stream().map(this::view).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public JsonNode get(UUID projectId, String resource, String externalId) {
        catalog.require(resource);
        return view(find(projectId, resource, externalId));
    }

    @Transactional
    public JsonNode createMutable(UUID projectId, String resource, JsonNode body, UUID actorId) {
        catalog.requireMutable(resource);
        return create(projectId, resource, body, actorId);
    }

    @Transactional
    public JsonNode create(UUID projectId, String resource, JsonNode body, UUID actorId) {
        catalog.require(resource);
        ObjectNode payload = payload(body);
        String legacyId = text(payload, "legacyId");
        if (legacyId == null) legacyId = preserveExternalId(text(payload, "id"));
        payload.remove(List.of("id", "uuid", "legacyId", "projectId", "createdAt", "updatedAt"));
        if (legacyId != null && records.findByProjectIdAndResourceTypeAndLegacyId(projectId, resource, legacyId)
                .isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "legacy_id_exists", "Legacy ID already exists");
        }
        ResourceRecord record = records.save(new ResourceRecord(projectId, resource, legacyId, payload, actorId));
        outbox.record(record, "created", actorId);
        return view(record);
    }

    @Transactional
    public JsonNode put(UUID projectId, String resource, String externalId, JsonNode body, UUID actorId) {
        catalog.requireMutable(resource);
        ObjectNode payload = payload(body);
        payload.remove(List.of("id", "uuid", "legacyId", "projectId", "createdAt", "updatedAt"));
        ResourceRecord record = findOptional(projectId, resource, externalId).orElseGet(() ->
                new ResourceRecord(projectId, resource, preserveExternalId(externalId), payload.deepCopy(), actorId));
        record.replace(payload);
        record = records.save(record);
        outbox.record(record, "updated", actorId);
        return view(record);
    }

    @Transactional
    public JsonNode patch(UUID projectId, String resource, String externalId, JsonNode body, UUID actorId) {
        catalog.requireMutable(resource);
        ResourceRecord record = find(projectId, resource, externalId);
        ObjectNode merged = payload(record.getPayload());
        ObjectNode patch = payload(body);
        patch.remove(List.of("id", "uuid", "legacyId", "projectId", "createdAt", "updatedAt"));
        for (Map.Entry<String, JsonNode> field : patch.properties()) {
            JsonNode value = field.getValue();
            if (value == null || value.isNull() || isLegacyDeleteField(value)) {
                merged.remove(field.getKey());
            } else {
                merged.set(field.getKey(), value);
            }
        }
        record.replace(merged);
        outbox.record(record, "updated", actorId);
        return view(record);
    }

    @Transactional
    public void delete(UUID projectId, String resource, String externalId, UUID actorId) {
        catalog.requireMutable(resource);
        ResourceRecord record = find(projectId, resource, externalId);
        outbox.record(record, "deleted", actorId);
        records.delete(record);
    }

    @Transactional
    public List<JsonNode> reorder(UUID projectId, String resource, JsonNode body, UUID actorId) {
        catalog.requireMutable(resource);
        if (!body.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_reorder", "Expected an array of updates");
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode update : body) {
            String id = text(update, "id");
            if (id == null) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_reorder", "id is required");
            ResourceRecord record = find(projectId, resource, id);
            ObjectNode merged = payload(record.getPayload());
            if (update.has("position")) merged.set("position", update.get("position"));
            if (update.has("order")) merged.set("order", update.get("order"));
            if (update.has("status")) merged.set("status", update.get("status"));
            record.replace(merged);
            outbox.record(record, "reordered", actorId);
            result.add(view(record));
        }
        return result;
    }

    private ResourceRecord find(UUID projectId, String resource, String externalId) {
        return findOptional(projectId, resource, externalId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "record_not_found", "Record not found"));
    }

    private java.util.Optional<ResourceRecord> findOptional(UUID projectId, String resource, String externalId) {
        try {
            var byId = records.findByProjectIdAndResourceTypeAndId(projectId, resource, UUID.fromString(externalId));
            return byId.isPresent() ? byId
                    : records.findByProjectIdAndResourceTypeAndLegacyId(projectId, resource, externalId);
        } catch (IllegalArgumentException ignored) {
            return records.findByProjectIdAndResourceTypeAndLegacyId(projectId, resource, externalId);
        }
    }

    private ObjectNode payload(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_body", "Expected a JSON object");
        }
        return ((ObjectNode) node).deepCopy();
    }

    public JsonNode view(ResourceRecord record) {
        ObjectNode view = payload(record.getPayload());
        view.put("id", record.getLegacyId() == null ? record.getId().toString() : record.getLegacyId());
        view.put("uuid", record.getId().toString());
        if (record.getLegacyId() != null) view.put("legacyId", record.getLegacyId());
        view.put("projectId", record.getProjectId().toString());
        view.put("createdAt", record.getCreatedAt().toString());
        view.put("updatedAt", record.getUpdatedAt().toString());
        return view;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()
                ? null : value.asText().trim();
    }

    private String preserveExternalId(String value) {
        // Record UUIDs and client business IDs are different namespaces. A
        // membership uses the user's UUID as its business ID, so stripping a
        // UUID here makes later PUT/PATCH/DELETE unable to find that record.
        return value;
    }

    private boolean isLegacyDeleteField(JsonNode value) {
        return value.isObject() && "deleteField".equals(value.path("_methodName").asText());
    }
}
