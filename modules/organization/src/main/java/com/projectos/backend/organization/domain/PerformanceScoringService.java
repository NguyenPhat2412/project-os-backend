package com.projectos.backend.organization.domain;

import com.projectos.backend.organization.web.PerformanceScoringController;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.api.PageResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceScoringService {
    public static final String VIEW_PERMISSION = "page:kpi-thi-dua";
    public static final String MANAGE_RULES_PERMISSION = "module:kpi-thi-dua.rules";
    public static final String ADJUST_POINTS_PERMISSION = "module:kpi-thi-dua.points";

    private final JdbcTemplate jdbc;
    private final OrganizationPermissionService permissions;

    public PerformanceScoringService(JdbcTemplate jdbc, OrganizationPermissionService permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public PageResponse<PerformanceScoringController.ScoringRuleView> listRules(UUID organizationId, int page, int size,
                                                                                 boolean activeOnly, String search,
                                                                                 UUID actorId, boolean root) {
        requirePage(page, size);
        permissions.requirePermission(organizationId, actorId, root, VIEW_PERMISSION);
        String term = clean(search);
        String filter = " where organization_id = ? and (? = false or is_active = true) "
                + "and (coalesce(?::text, '') = '' or rule_code ilike ? or name ilike ? or category ilike ?)";
        long total = jdbc.queryForObject("select count(*) from performance_scoring_rules" + filter,
                Long.class, organizationId, activeOnly, term, like(term), like(term), like(term));
        List<PerformanceScoringController.ScoringRuleView> rows = jdbc.query(
                "select id, organization_id, rule_code, name, description, category, points, is_active, created_at, updated_at "
                        + "from performance_scoring_rules" + filter + " order by rule_code asc limit ? offset ?",
                (rs, rowNum) -> new PerformanceScoringController.ScoringRuleView(
                        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                        rs.getString("rule_code"), rs.getString("name"), rs.getString("description"),
                        rs.getString("category"), rs.getInt("points"), rs.getBoolean("is_active"),
                        instant(rs, "created_at"), instant(rs, "updated_at")),
                organizationId, activeOnly, term, like(term), like(term), like(term), size, page * size);
        return PageResponse.of(rows, page, size, total, pages(total, size));
    }

    @Transactional
    public PerformanceScoringController.ScoringRuleView createRule(UUID organizationId,
                                                                     PerformanceScoringController.ScoringRuleRequest request,
                                                                     UUID actorId, boolean root) {
        permissions.requirePermission(organizationId, actorId, root, MANAGE_RULES_PERMISSION);
        String ruleCode = requiredCode(request.ruleCode());
        String name = required(request.name(), "Tên quy tắc");
        String category = required(request.category(), "Phân loại");
        int points = boundedPoints(request.points());
        try {
            UUID id = UUID.randomUUID();
            jdbc.update("insert into performance_scoring_rules "
                            + "(id, organization_id, rule_code, name, description, category, points, is_active, created_by, updated_by) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, organizationId, ruleCode, name, clean(request.description()), category, points,
                    request.isActive() == null || request.isActive(), actorId, actorId);
            return rule(organizationId, id);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "scoring_rule_code_exists", "Mã quy tắc đã tồn tại trong tổ chức.");
        }
    }

    @Transactional
    public PerformanceScoringController.ScoringRuleView updateRule(UUID organizationId, UUID ruleId,
                                                                     PerformanceScoringController.ScoringRulePatch request,
                                                                     UUID actorId, boolean root) {
        permissions.requirePermission(organizationId, actorId, root, MANAGE_RULES_PERMISSION);
        requireRule(organizationId, ruleId);
        String code = request.ruleCode() == null ? null : requiredCode(request.ruleCode());
        String name = request.name() == null ? null : required(request.name(), "Tên quy tắc");
        String category = request.category() == null ? null : required(request.category(), "Phân loại");
        Integer points = request.points() == null ? null : boundedPoints(request.points());
        try {
            jdbc.update("update performance_scoring_rules set rule_code = coalesce(?, rule_code), "
                            + "name = coalesce(?, name), description = coalesce(?, description), "
                            + "category = coalesce(?, category), points = coalesce(?, points), "
                            + "is_active = coalesce(?, is_active), updated_by = ?, updated_at = now() "
                            + "where organization_id = ? and id = ?",
                    code, name, clean(request.description()), category, points, request.isActive(), actorId, organizationId, ruleId);
            return rule(organizationId, ruleId);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "scoring_rule_code_exists", "Mã quy tắc đã tồn tại trong tổ chức.");
        }
    }

    @Transactional
    public void deactivateRule(UUID organizationId, UUID ruleId, UUID actorId, boolean root) {
        permissions.requirePermission(organizationId, actorId, root, MANAGE_RULES_PERMISSION);
        requireRule(organizationId, ruleId);
        jdbc.update("update performance_scoring_rules set is_active = false, updated_by = ?, updated_at = now() "
                        + "where organization_id = ? and id = ?", actorId, organizationId, ruleId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PerformanceScoringController.ScoreEventView> listEvents(UUID organizationId, int page, int size,
                                                                                 UUID employeeId, String from, String to,
                                                                                 String search, UUID actorId, boolean root) {
        requirePage(page, size);
        permissions.requirePermission(organizationId, actorId, root, VIEW_PERMISSION);
        Timestamp fromValue = startOf(from);
        Timestamp toValue = endOf(to);
        String term = clean(search);
        String filter = " where ev.organization_id = ? and (?::uuid is null or ev.employee_id = ?::uuid) "
                + "and ev.occurred_at >= coalesce(?::timestamptz, '-infinity'::timestamptz) "
                + "and ev.occurred_at < coalesce(?::timestamptz, 'infinity'::timestamptz) "
                + "and (coalesce(?::text, '') = '' or ev.source ilike ? or ev.reason ilike ? or r.rule_code ilike ?)";
        Object[] params = {organizationId, employeeId, employeeId, fromValue, toValue, term, like(term), like(term), like(term)};
        long total = jdbc.queryForObject("select count(*) from employee_score_events ev left join performance_scoring_rules r on r.id = ev.rule_id" + filter,
                Long.class, params);
        List<PerformanceScoringController.ScoreEventView> rows = jdbc.query(
                "select ev.id, ev.organization_id, ev.employee_id, e.code employee_code, e.full_name employee_name, "
                        + "r.rule_code, ev.source, ev.event_key, ev.points, ev.reason, ev.occurred_at, ev.created_at "
                        + "from employee_score_events ev join employees e on e.id = ev.employee_id "
                        + "left join performance_scoring_rules r on r.id = ev.rule_id" + filter
                        + " order by ev.occurred_at desc limit ? offset ?",
                (rs, rowNum) -> new PerformanceScoringController.ScoreEventView(
                        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                        rs.getObject("employee_id", UUID.class), rs.getString("employee_code"),
                        rs.getString("employee_name"), rs.getString("rule_code"), rs.getString("source"),
                        rs.getString("event_key"), rs.getInt("points"), rs.getString("reason"),
                        instant(rs, "occurred_at"), instant(rs, "created_at")),
                append(params, size, page * size));
        return PageResponse.of(rows, page, size, total, pages(total, size));
    }

    @Transactional
    public PerformanceScoringController.ScoreEventView createEvent(UUID organizationId,
                                                                    PerformanceScoringController.ScoreEventRequest request,
                                                                    UUID actorId, boolean root) {
        return createEvent(organizationId, request, actorId, root, false);
    }

    private PerformanceScoringController.ScoreEventView createEvent(UUID organizationId,
                                                                     PerformanceScoringController.ScoreEventRequest request,
                                                                     UUID actorId, boolean root,
                                                                     boolean compatibilityAward) {
        permissions.requirePermission(organizationId, actorId, root,
                compatibilityAward ? VIEW_PERMISSION : ADJUST_POINTS_PERMISSION);
        if (request.eventKey() != null) {
            PerformanceScoringController.ScoreEventView existing = eventByKey(organizationId, request.eventKey());
            if (existing != null) return existing;
        }
        requireEmployee(organizationId, request.employeeId());
        UUID ruleId = null;
        Integer points = request.points();
        if (request.ruleCode() != null && !request.ruleCode().isBlank()) {
            RuleRecord rule = activeRule(organizationId, request.ruleCode());
            ruleId = rule.id();
            if (points == null) points = rule.points();
        }
        if (points == null) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_score_event", "Số điểm hoặc quy tắc phải được cung cấp.");
        boundedPoints(points);
        String reason = required(request.reason(), "Lý do");
        String source = request.source() == null || request.source().isBlank() ? "MANUAL_ADJUSTMENT" : request.source().trim();
        try {
            UUID id = UUID.randomUUID();
            jdbc.update("insert into employee_score_events "
                            + "(id, organization_id, employee_id, rule_id, source, event_key, points, reason, occurred_at, created_by) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, coalesce(?, now()), ?)",
                    id, organizationId, request.employeeId(), ruleId, source, clean(request.eventKey()), points, reason,
                    request.occurredAt() == null ? null : Timestamp.from(request.occurredAt()), actorId);
            return event(organizationId, id);
        } catch (DuplicateKeyException exception) {
            PerformanceScoringController.ScoreEventView existing = eventByKey(organizationId, request.eventKey());
            if (existing != null) return existing;
            throw new ApiException(HttpStatus.CONFLICT, "duplicate_score_event", "Sự kiện điểm đã tồn tại.");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PerformanceScoringController.ScoreboardRow> scoreboard(UUID organizationId, int page, int size,
                                                                                 String from, String to, UUID actorId, boolean root) {
        requirePage(page, size);
        permissions.requirePermission(organizationId, actorId, root, VIEW_PERMISSION);
        Timestamp fromValue = startOf(from == null ? LocalDate.now().withDayOfMonth(1).toString() : from);
        Timestamp toValue = endOf(to == null ? LocalDate.now().plusMonths(1).withDayOfMonth(1).toString() : to);
        String cte = "with scored as (select e.id employee_id, e.code employee_code, e.full_name employee_name, "
                + "avg(k.score_percent) kpi_score, coalesce(sum(ev.points), 0) event_points "
                + "from employees e left join enterprise_kpi_evaluations k on k.employee_uuid = e.id "
                + "and k.created_at >= ? and k.created_at < ? "
                + "left join employee_score_events ev on ev.employee_id = e.id and ev.organization_id = e.organization_id "
                + "and ev.occurred_at >= ? and ev.occurred_at < ? "
                + "where e.organization_id = ? and e.is_deleted = false "
                + "group by e.id, e.code, e.full_name) ";
        long total = jdbc.queryForObject("select count(*) from employees where organization_id = ? and is_deleted = false", Long.class, organizationId);
        List<PerformanceScoringController.ScoreboardRow> rows = jdbc.query(
                cte + "select row_number() over (order by (coalesce(kpi_score, 0) + event_points) desc, employee_name asc) rank, "
                        + "employee_id, employee_code, employee_name, kpi_score, event_points, "
                        + "(coalesce(kpi_score, 0) + event_points) total_score from scored "
                        + "order by total_score desc, employee_name asc limit ? offset ?",
                (rs, rowNum) -> new PerformanceScoringController.ScoreboardRow(
                        rs.getLong("rank"), rs.getObject("employee_id", UUID.class), rs.getString("employee_code"),
                        rs.getString("employee_name"), decimal(rs, "kpi_score"), rs.getInt("event_points"),
                        decimal(rs, "total_score")),
                fromValue, toValue, fromValue, toValue, organizationId, size, page * size);
        return PageResponse.of(rows, page, size, total, pages(total, size));
    }

    @Transactional(readOnly = true)
    public PerformanceScoringController.ScoreSummary summary(UUID organizationId, String employeeCode,
                                                              UUID actorId, boolean root) {
        permissions.requirePermission(organizationId, actorId, root, VIEW_PERMISSION);
        String code = required(employeeCode, "Mã nhân viên");
        return jdbc.query("select e.id, e.code, e.full_name, coalesce(sum(ev.points), 0) total_points "
                        + "from employees e left join employee_score_events ev on ev.employee_id = e.id "
                        + "and ev.organization_id = e.organization_id where e.organization_id = ? "
                        + "and e.is_deleted = false and lower(e.code) = lower(?) "
                        + "group by e.id, e.code, e.full_name",
                (rs, rowNum) -> {
                    int points = rs.getInt("total_points");
                    return new PerformanceScoringController.ScoreSummary(
                            rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("full_name"),
                            points, tier(points));
                }, organizationId, code).stream().findFirst()
                .orElseThrow(() -> notFound("employee_not_found", "Nhân sự không tồn tại trong hệ thống."));
    }

    @Transactional
    public PerformanceScoringController.PointAwardResult awardCompatibility(UUID organizationId,
                                                                             PerformanceScoringController.PointAwardRequest request,
                                                                             UUID actorId, boolean root) {
        permissions.requirePermission(organizationId, actorId, root, VIEW_PERMISSION);
        RuleRecord rule = activeRuleOrNull(organizationId, request.ruleCode());
        if (rule == null) return new PerformanceScoringController.PointAwardResult(null, 0, false);
        UUID employeeId = jdbc.query("select id from employees where organization_id = ? and is_deleted = false "
                        + "and lower(code) = lower(?)", (rs, rowNum) -> rs.getObject("id", UUID.class), organizationId, request.employeeCode())
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "employee_not_found", "Nhân sự không tồn tại trong hệ thống."));
        String eventKey = clean(request.eventKey());
        if (eventKey != null) {
            PerformanceScoringController.ScoreEventView existing = eventByKey(organizationId, eventKey);
            if (existing != null) return new PerformanceScoringController.PointAwardResult(existing.id(), existing.points(), false);
        }
        PerformanceScoringController.ScoreEventView event = createEvent(organizationId,
                new PerformanceScoringController.ScoreEventRequest(employeeId, rule.code(), request.source(), eventKey,
                        null, request.reason(), Instant.now()), actorId, root, true);
        return new PerformanceScoringController.PointAwardResult(event.id(), event.points(), true);
    }

    private PerformanceScoringController.ScoringRuleView rule(UUID organizationId, UUID id) {
        return jdbc.query("select id, organization_id, rule_code, name, description, category, points, is_active, created_at, updated_at "
                        + "from performance_scoring_rules where organization_id = ? and id = ?",
                (rs, rowNum) -> new PerformanceScoringController.ScoringRuleView(
                        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getString("rule_code"),
                        rs.getString("name"), rs.getString("description"), rs.getString("category"), rs.getInt("points"),
                        rs.getBoolean("is_active"), instant(rs, "created_at"), instant(rs, "updated_at")),
                organizationId, id).stream().findFirst().orElseThrow(() -> notFound("scoring_rule_not_found", "Quy tắc điểm không tồn tại."));
    }

    private PerformanceScoringController.ScoreEventView event(UUID organizationId, UUID id) {
        return jdbc.query("select ev.id, ev.organization_id, ev.employee_id, e.code employee_code, e.full_name employee_name, "
                        + "r.rule_code, ev.source, ev.event_key, ev.points, ev.reason, ev.occurred_at, ev.created_at "
                        + "from employee_score_events ev join employees e on e.id = ev.employee_id "
                        + "left join performance_scoring_rules r on r.id = ev.rule_id where ev.organization_id = ? and ev.id = ?",
                (rs, rowNum) -> new PerformanceScoringController.ScoreEventView(
                        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getObject("employee_id", UUID.class),
                        rs.getString("employee_code"), rs.getString("employee_name"), rs.getString("rule_code"), rs.getString("source"),
                        rs.getString("event_key"), rs.getInt("points"), rs.getString("reason"), instant(rs, "occurred_at"),
                        instant(rs, "created_at")), organizationId, id).stream().findFirst()
                .orElseThrow(() -> notFound("score_event_not_found", "Sự kiện điểm không tồn tại."));
    }

    private PerformanceScoringController.ScoreEventView eventByKey(UUID organizationId, String eventKey) {
        if (eventKey == null || eventKey.isBlank()) return null;
        return jdbc.query("select ev.id, ev.organization_id, ev.employee_id, e.code employee_code, e.full_name employee_name, "
                        + "r.rule_code, ev.source, ev.event_key, ev.points, ev.reason, ev.occurred_at, ev.created_at "
                        + "from employee_score_events ev join employees e on e.id = ev.employee_id "
                        + "left join performance_scoring_rules r on r.id = ev.rule_id where ev.organization_id = ? and ev.event_key = ?",
                (rs, rowNum) -> new PerformanceScoringController.ScoreEventView(
                        rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getObject("employee_id", UUID.class),
                        rs.getString("employee_code"), rs.getString("employee_name"), rs.getString("rule_code"), rs.getString("source"),
                        rs.getString("event_key"), rs.getInt("points"), rs.getString("reason"), instant(rs, "occurred_at"),
                        instant(rs, "created_at")), organizationId, eventKey).stream().findFirst().orElse(null);
    }

    private RuleRecord activeRule(UUID organizationId, String code) {
        RuleRecord rule = activeRuleOrNull(organizationId, code);
        if (rule == null) throw notFound("scoring_rule_not_found", "Quy tắc điểm đang hoạt động không tồn tại.");
        return rule;
    }

    private RuleRecord activeRuleOrNull(UUID organizationId, String code) {
        if (code == null || code.isBlank()) return null;
        return jdbc.query("select id, rule_code, points from performance_scoring_rules "
                        + "where organization_id = ? and rule_code = ? and is_active = true",
                (rs, rowNum) -> new RuleRecord(rs.getObject("id", UUID.class), rs.getString("rule_code"), rs.getInt("points")),
                organizationId, code.trim().toUpperCase(Locale.ROOT)).stream().findFirst().orElse(null);
    }

    private void requireRule(UUID organizationId, UUID ruleId) {
        if (jdbc.queryForObject("select count(*) from performance_scoring_rules where organization_id = ? and id = ?",
                Integer.class, organizationId, ruleId) == 0) throw notFound("scoring_rule_not_found", "Quy tắc điểm không tồn tại.");
    }

    private void requireEmployee(UUID organizationId, UUID employeeId) {
        if (jdbc.queryForObject("select count(*) from employees where organization_id = ? and id = ? and is_deleted = false",
                Integer.class, organizationId, employeeId) == 0) throw notFound("employee_not_found", "Nhân sự không tồn tại trong hệ thống.");
    }

    private static BigDecimal decimal(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Object[] append(Object[] source, Object... values) {
        Object[] result = new Object[source.length + values.length];
        System.arraycopy(source, 0, result, 0, source.length);
        System.arraycopy(values, 0, result, source.length, values.length);
        return result;
    }

    private static String requiredCode(String value) {
        String result = required(value, "Mã quy tắc").toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9][A-Z0-9_-]{1,79}")) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_rule_code", "Mã quy tắc không hợp lệ.");
        return result;
    }

    private static String required(String value, String field) {
        String result = clean(value);
        if (result == null) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_score_rule", field + " là bắt buộc.");
        return result;
    }

    private static Integer boundedPoints(Integer value) {
        if (value == null || value < -1_000_000 || value > 1_000_000) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_score_points", "Số điểm phải nằm trong khoảng cho phép.");
        return value;
    }

    private static String tier(int points) {
        if (points >= 100) return "Xuất sắc";
        if (points >= 50) return "Tích cực";
        if (points > 0) return "Đang phát triển";
        return "Chưa xếp hạng";
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String like(String value) { return value == null ? null : "%" + value + "%"; }
    private static int pages(long total, int size) { return (int) Math.ceil((double) total / size); }
    private static void requirePage(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "Tham số phân trang không hợp lệ."); }
    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    private static Timestamp startOf(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Timestamp.from(LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC)); }
        catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_score_period", "Khoảng thời gian không hợp lệ."); }
    }

    private static Timestamp endOf(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Timestamp.from(LocalDate.parse(value).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)); }
        catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_score_period", "Khoảng thời gian không hợp lệ."); }
    }

    private record RuleRecord(UUID id, String code, int points) {}
}
