package com.projectos.backend.organization.domain;

import com.projectos.backend.organization.web.FeaturePositionProfileController;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.api.PageResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FeaturePositionProfileService {
    private static final int MAX_PAGE_SIZE = 200;
    private static final List<String> ICON_BACKGROUNDS = List.of("blue", "red", "yellow");

    private final JdbcTemplate jdbc;
    private final OrganizationPermissionService permissions;
    private final ObjectMapper mapper;
    private final String tableName;

    public FeaturePositionProfileService(JdbcTemplate jdbc, OrganizationPermissionService permissions, ObjectMapper mapper,
                                         @Value("${app.organization-schema:public}") String organizationSchema) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.mapper = mapper;
        this.tableName = qualifiedTable(organizationSchema);
    }

    @Transactional(readOnly = true)
    public PageResponse<FeaturePositionProfileController.FeaturePositionProfileView> list(
            UUID organizationId, int page, int size, UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        requirePage(page, size);
        long total = jdbc.queryForObject(
                "select count(*) from " + tableName + " where organization_id = ?",
                Long.class, organizationId);
        List<FeaturePositionProfileController.FeaturePositionProfileView> rows = jdbc.query(
                "select id, organization_id, name, code, department, description, icon_bg, "
                        + "allowed_feature_keys, created_at, updated_at "
                        + "from " + tableName + " where organization_id = ? "
                        + "order by name asc, code asc limit ? offset ?",
                (rs, rowNum) -> view(rs), organizationId, size, page * size);
        return PageResponse.of(rows, page, size, total, pages(total, size));
    }

    @Transactional
    public FeaturePositionProfileController.FeaturePositionProfileView create(
            UUID organizationId, FeaturePositionProfileController.FeaturePositionProfileRequest request,
            UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        String name = required(request.name(), "Tên vị trí");
        String code = requiredCode(request.code());
        String iconBg = iconBackground(request.iconBg());
        List<String> featureKeys = featureKeys(request.allowedFeatureKeys());
        try {
            UUID id = UUID.randomUUID();
            jdbc.update("insert into " + tableName + " "
                            + "(id, organization_id, name, code, department, description, icon_bg, allowed_feature_keys, created_by, updated_by) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                    id, organizationId, name, code, clean(request.department()), clean(request.description()), iconBg,
                    json(featureKeys), actorId, actorId);
            return get(organizationId, id);
        } catch (DuplicateKeyException exception) {
            throw conflict("feature_position_code_exists", "Mã vị trí đã tồn tại trong tổ chức.");
        }
    }

    @Transactional
    public FeaturePositionProfileController.FeaturePositionProfileView update(
            UUID organizationId, UUID profileId, FeaturePositionProfileController.FeaturePositionProfilePatch request,
            UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        FeaturePositionProfileController.FeaturePositionProfileView current = get(organizationId, profileId);
        String name = request.name() == null ? current.name() : required(request.name(), "Tên vị trí");
        String code = request.code() == null ? current.code() : requiredCode(request.code());
        String iconBg = request.iconBg() == null ? current.iconBg() : iconBackground(request.iconBg());
        List<String> featureKeys = request.allowedFeatureKeys() == null
                ? current.allowedFeatureKeys() : featureKeys(request.allowedFeatureKeys());
        try {
            jdbc.update("update " + tableName + " set name = ?, code = ?, department = ?, description = ?, "
                            + "icon_bg = ?, allowed_feature_keys = ?::jsonb, updated_by = ?, updated_at = now() "
                            + "where organization_id = ? and id = ?",
                    name, code, request.department() == null ? current.department() : clean(request.department()),
                    request.description() == null ? current.description() : clean(request.description()), iconBg,
                    json(featureKeys), actorId, organizationId, profileId);
            return get(organizationId, profileId);
        } catch (DuplicateKeyException exception) {
            throw conflict("feature_position_code_exists", "Mã vị trí đã tồn tại trong tổ chức.");
        }
    }

    @Transactional
    public void delete(UUID organizationId, UUID profileId, UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        get(organizationId, profileId);
        jdbc.update("delete from " + tableName + " where organization_id = ? and id = ?",
                organizationId, profileId);
    }

    private FeaturePositionProfileController.FeaturePositionProfileView get(UUID organizationId, UUID profileId) {
        return jdbc.query("select id, organization_id, name, code, department, description, icon_bg, "
                        + "allowed_feature_keys, created_at, updated_at from " + tableName + " "
                        + "where organization_id = ? and id = ?",
                (rs, rowNum) -> view(rs), organizationId, profileId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "feature_position_profile_not_found",
                        "Vị trí phân quyền không tồn tại trong hệ thống."));
    }

    private FeaturePositionProfileController.FeaturePositionProfileView view(ResultSet rs) throws SQLException {
        return new FeaturePositionProfileController.FeaturePositionProfileView(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getString("name"),
                rs.getString("code"), rs.getString("department"), rs.getString("description"), rs.getString("icon_bg"),
                parseKeys(rs.getString("allowed_feature_keys")), true, instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private List<String> parseKeys(String value) {
        try {
            JsonNode node = mapper.readTree(value);
            List<String> keys = new ArrayList<>();
            node.forEach(item -> keys.add(item.asText()));
            return List.copyOf(keys);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "feature_position_data_invalid",
                    "Dữ liệu cấu hình vị trí không hợp lệ.");
        }
    }

    private List<String> featureKeys(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 300) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "feature_position_keys_required",
                    "Vị trí phải có ít nhất một tính năng được cấp quyền.");
        }
        List<String> normalized = values.stream().map(this::requiredFeatureKey).distinct().toList();
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "feature_position_keys_required",
                    "Vị trí phải có ít nhất một tính năng được cấp quyền.");
        }
        return normalized;
    }

    private String requiredFeatureKey(String value) {
        String normalized = clean(value);
        if (normalized == null || normalized.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_feature_key", "Mã tính năng không hợp lệ.");
        }
        return normalized;
    }

    private String required(String value, String field) {
        String normalized = clean(value);
        if (normalized == null) throw new ApiException(HttpStatus.BAD_REQUEST, "required_field", field + " là bắt buộc.");
        return normalized;
    }

    private String requiredCode(String value) {
        String normalized = required(value, "Mã vị trí").toUpperCase(Locale.ROOT);
        if (normalized.length() > 80) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_feature_position_code", "Mã vị trí quá dài.");
        return normalized;
    }

    private String iconBackground(String value) {
        String normalized = required(value, "Màu đại diện").toLowerCase(Locale.ROOT);
        if (!ICON_BACKGROUNDS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_feature_position_icon", "Màu đại diện không hợp lệ.");
        }
        return normalized;
    }

    private String clean(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String json(List<String> values) {
        return mapper.writeValueAsString(values);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "Thông số phân trang không hợp lệ.");
        }
    }

    private int pages(long total, int size) { return total == 0 ? 0 : (int) Math.ceil((double) total / size); }

    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }

    private String qualifiedTable(String schema) {
        String normalized = schema == null ? "public" : schema.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalStateException("Invalid organization database schema");
        }
        return normalized + ".organization_feature_position_profiles";
    }
}
