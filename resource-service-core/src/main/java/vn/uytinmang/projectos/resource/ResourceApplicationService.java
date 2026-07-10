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

    public ResourceApplicationService(ResourceRecordRepository records, ResourceCatalog catalog) {
        this.records = records;
        this.catalog = catalog;
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
    public JsonNode create(UUID projectId, String resource, JsonNode body, UUID actorId) {
        catalog.require(resource);
        ObjectNode payload = payload(body);
        String legacyId = text(payload, "legacyId");
        if (legacyId == null) legacyId = nonUuid(text(payload, "id"));
        payload.remove(List.of("id", "uuid", "legacyId", "projectId", "createdAt", "updatedAt"));
        if (legacyId != null && records.findByProjectIdAndResourceTypeAndLegacyId(projectId, resource, legacyId)
                .isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "legacy_id_exists", "Legacy ID already exists");
        }
        return view(records.save(new ResourceRecord(projectId, resource, legacyId, payload, actorId)));
    }

    @Transactional
    public JsonNode put(UUID projectId, String resource, String externalId, JsonNode body, UUID actorId) {
        catalog.requireMutable(resource);
        ObjectNode payload = payload(body);
        payload.remove(List.of("id", "uuid", "legacyId", "projectId", "createdAt", "updatedAt"));
        ResourceRecord record = findOptional(projectId, resource, externalId).orElseGet(() ->
                new ResourceRecord(projectId, resource, nonUuid(externalId), payload.deepCopy(), actorId));
        record.replace(payload);
        return view(records.save(record));
    }

    @Transactional
    public JsonNode patch(UUID projectId, String resource, String externalId, JsonNode body) {
        catalog.requireMutable(resource);
        ResourceRecord record = find(projectId, resource, externalId);
        ObjectNode merged = payload(record.getPayload());
        ObjectNode patch = payload(body);
        patch.remove(List.of("id", "uuid", "legacyId", "projectId", "createdAt", "updatedAt"));
        for (Map.Entry<String, JsonNode> field : patch.properties()) {
            merged.set(field.getKey(), field.getValue());
        }
        record.replace(merged);
        return view(record);
    }

    @Transactional
    public void delete(UUID projectId, String resource, String externalId) {
        catalog.requireMutable(resource);
        records.delete(find(projectId, resource, externalId));
    }

    @Transactional
    public List<JsonNode> reorder(UUID projectId, String resource, JsonNode body) {
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
            return records.findByProjectIdAndResourceTypeAndId(projectId, resource, UUID.fromString(externalId));
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

    private JsonNode view(ResourceRecord record) {
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

    private String nonUuid(String value) {
        if (value == null) return null;
        try {
            UUID.fromString(value);
            return null;
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }
}
