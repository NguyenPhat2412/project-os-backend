package com.projectos.backend.organization.domain;

import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.organization.NotificationCategoryDirectory;
import com.projectos.backend.organization.web.NotificationCategoryController;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationCategoryService implements NotificationCategoryDirectory {
    private final JdbcTemplate jdbc;
    private final OrganizationPermissionService permissions;
    private final String schema;

    public NotificationCategoryService(JdbcTemplate jdbc, OrganizationPermissionService permissions,
                                       @Value("${app.organization-schema:public}") String schema) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.schema = schema;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> activeCategories(UUID organizationId) {
        return jdbc.query("select id, code, name, display_order from " + table()
                        + " where organization_id = ? and is_active = true order by display_order, name",
                (rs, row) -> new Category(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getInt("display_order")), organizationId);
    }

    @Transactional(readOnly = true)
    public List<NotificationCategoryController.CategoryView> list(UUID organizationId, boolean includeInactive,
                                                                    UUID actorId, boolean root) {
        if (includeInactive) permissions.requireOrganizationAdmin(organizationId, actorId, root);
        else permissions.requireOrganizationMember(organizationId, actorId, root);
        String activeFilter = includeInactive ? "" : " and is_active = true";
        return jdbc.query("select id, organization_id, code, name, is_active, display_order, created_by, created_at, updated_at "
                        + "from " + table() + " where organization_id = ?" + activeFilter
                        + " order by display_order, name",
                (rs, row) -> view(rs), organizationId);
    }

    @Transactional
    public NotificationCategoryController.CategoryView create(UUID organizationId,
                                                                NotificationCategoryController.CategoryRequest request,
                                                                UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        String code = code(request.code());
        String name = name(request.name());
        try {
            UUID id = UUID.randomUUID();
            jdbc.update("insert into " + table()
                            + " (id, organization_id, code, name, is_active, display_order, created_by, updated_by) "
                            + "values (?, ?, ?, ?, true, ?, ?, ?)",
                    id, organizationId, code, name, order(request.displayOrder()), actorId, actorId);
            return find(organizationId, id);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "notification_category_code_exists",
                    "Mã phân loại thông báo đã tồn tại trong công ty.");
        }
    }

    @Transactional
    public NotificationCategoryController.CategoryView update(UUID organizationId, UUID id,
                                                                NotificationCategoryController.CategoryPatch request,
                                                                UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        require(organizationId, id);
        String code = request.code() == null ? null : code(request.code());
        String name = request.name() == null ? null : name(request.name());
        try {
            jdbc.update("update " + table() + " set code = coalesce(?, code), name = coalesce(?, name), "
                            + "is_active = coalesce(?, is_active), display_order = coalesce(?, display_order), "
                            + "updated_by = ?, updated_at = now() where organization_id = ? and id = ?",
                    code, name, request.isActive(), request.displayOrder(), actorId, organizationId, id);
            return find(organizationId, id);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "notification_category_code_exists",
                    "Mã phân loại thông báo đã tồn tại trong công ty.");
        }
    }

    @Transactional
    public void deactivate(UUID organizationId, UUID id, UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        require(organizationId, id);
        jdbc.update("update " + table() + " set is_active = false, updated_by = ?, updated_at = now() "
                        + "where organization_id = ? and id = ?", actorId, organizationId, id);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveCategory(UUID organizationId, String rawCode) {
        String normalized = code(rawCode);
        Integer count = jdbc.queryForObject("select count(*) from " + table()
                        + " where organization_id = ? and code = ? and is_active = true", Integer.class,
                organizationId, normalized);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "notification_category_invalid",
                    "Phân loại thông báo chưa được công ty kích hoạt.");
        }
    }

    private NotificationCategoryController.CategoryView find(UUID organizationId, UUID id) {
        return jdbc.query("select id, organization_id, code, name, is_active, display_order, created_by, created_at, updated_at "
                        + "from " + table() + " where organization_id = ? and id = ?", (rs, row) -> view(rs),
                organizationId, id).stream().findFirst().orElseThrow(() -> notFound());
    }

    private void require(UUID organizationId, UUID id) {
        if (jdbc.queryForObject("select count(*) from " + table()
                        + " where organization_id = ? and id = ?", Integer.class, organizationId, id) == 0) {
            throw notFound();
        }
    }

    private NotificationCategoryController.CategoryView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NotificationCategoryController.CategoryView(rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("is_active"), rs.getInt("display_order"), rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private String code(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{1,79}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_notification_category_code",
                    "Mã phân loại chỉ gồm chữ không dấu, số, gạch ngang hoặc gạch dưới.");
        }
        return normalized;
    }

    private String name(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 160) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_notification_category_name",
                    "Tên phân loại thông báo không hợp lệ.");
        }
        return normalized;
    }

    private int order(Integer value) {
        if (value == null || value < 0) return 0;
        return value;
    }

    private String table() { return schema + ".notification_categories"; }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "notification_category_not_found",
                "Không tìm thấy phân loại thông báo.");
    }
}
