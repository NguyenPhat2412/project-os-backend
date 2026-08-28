package com.projectos.backend.organization.domain;

import com.projectos.backend.organization.web.SalaryRegulationController;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.api.PageResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
public class SalaryRegulationService {
    public static final String VIEW_PERMISSION = "page:salary-regulations";
    public static final String MANAGE_PERMISSION = "module:salary-regulations.rules";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final JdbcTemplate jdbc;
    private final OrganizationPermissionService permissions;
    private final String table;

    public SalaryRegulationService(JdbcTemplate jdbc, OrganizationPermissionService permissions,
                                   @Value("${app.organization-schema:public}") String schema) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.table = ("organization".equals(schema) ? "organization" : "public") + ".salary_regulations";
    }

    @Transactional(readOnly = true)
    public PageResponse<SalaryRegulationController.SalaryRegulationView> list(UUID organizationId, int page, int size,
                                                                                String search, String status,
                                                                                String salaryType, UUID actorId, boolean root) {
        requirePage(page, size);
        permissions.requireOrganizationMember(organizationId, actorId, root);
        String term = clean(search);
        String normalizedStatus = clean(status).toUpperCase(Locale.ROOT);
        String normalizedType = clean(salaryType);
        String filter = " where organization_id = ? and (? = '' or rule_code ilike ? or name ilike ? or notes ilike ?)"
                + " and (? = '' or status = ?) and (? = '' or salary_type = ?)";
        Object[] countArgs = {organizationId, term, like(term), like(term), like(term), normalizedStatus, normalizedStatus,
                normalizedType, normalizedType};
        long total = jdbc.queryForObject("select count(*) from " + table + filter, Long.class, countArgs);
        Object[] pageArgs = {organizationId, term, like(term), like(term), like(term), normalizedStatus, normalizedStatus,
                normalizedType, normalizedType, size, page * size};
        List<SalaryRegulationController.SalaryRegulationView> rows = jdbc.query(
                "select id, organization_id, rule_code, name, salary_type, grade_step, coefficient, min_amount, max_amount, "
                        + "base_salary, title_salary, performance_salary, concurrent_allowance, gasoline_allowance, "
                        + "other_allowance, total_salary, effective_date, status, notes, created_by, created_at, updated_at "
                        + "from " + table + filter + " order by effective_date desc, rule_code asc limit ? offset ?",
                this::view, pageArgs);
        return PageResponse.of(rows, page, size, total, pages(total, size));
    }

    @Transactional(readOnly = true)
    public List<String> categories(UUID organizationId, UUID actorId, boolean root) {
        permissions.requireOrganizationMember(organizationId, actorId, root);
        return jdbc.queryForList("select distinct salary_type from " + table + " "
                + "where organization_id = ? and salary_type is not null and trim(salary_type) <> '' order by salary_type",
                String.class, organizationId);
    }

    @Transactional
    public SalaryRegulationController.SalaryRegulationView create(UUID organizationId,
                                                                   SalaryRegulationController.SalaryRegulationRequest request,
                                                                   UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        String code = requiredCode(request.ruleCode());
        String name = required(request.name(), "Tên quy chế");
        String type = required(request.salaryType(), "Loại lương");
        LocalDate effectiveDate = requiredDate(request.effectiveDate());
        String status = status(request.status());
        Amounts amounts = amounts(request);
        try {
            UUID id = UUID.randomUUID();
            jdbc.update("insert into " + table + " (id, organization_id, rule_code, name, salary_type, grade_step, coefficient, "
                            + "min_amount, max_amount, base_salary, title_salary, performance_salary, concurrent_allowance, "
                            + "gasoline_allowance, other_allowance, total_salary, effective_date, status, notes, created_by) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, organizationId, code, name, type, clean(request.gradeStep()), amount(request.coefficient()), amounts.min,
                    amounts.max, amounts.base, amounts.title, amounts.performance, amounts.concurrent, amounts.gasoline,
                    amounts.other, amounts.total, effectiveDate, status, clean(request.notes()), actorId);
            return get(organizationId, id);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "salary_regulation_code_exists", "Mã quy chế đã tồn tại trong doanh nghiệp.");
        }
    }

    @Transactional
    public SalaryRegulationController.SalaryRegulationView update(UUID organizationId, UUID id,
                                                                   SalaryRegulationController.SalaryRegulationPatch request,
                                                                   UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        SalaryRegulationController.SalaryRegulationView current = get(organizationId, id);
        String code = request.ruleCode() == null ? current.ruleCode() : requiredCode(request.ruleCode());
        String name = request.name() == null ? current.name() : required(request.name(), "Tên quy chế");
        String type = request.salaryType() == null ? current.salaryType() : required(request.salaryType(), "Loại lương");
        LocalDate effectiveDate = request.effectiveDate() == null ? current.effectiveDate() : requiredDate(request.effectiveDate());
        String status = request.status() == null ? current.status() : status(request.status());
        Amounts amounts = amounts(request, current);
        try {
            jdbc.update("update " + table + " set rule_code = ?, name = ?, salary_type = ?, grade_step = ?, coefficient = ?, "
                            + "min_amount = ?, max_amount = ?, base_salary = ?, title_salary = ?, performance_salary = ?, "
                            + "concurrent_allowance = ?, gasoline_allowance = ?, other_allowance = ?, total_salary = ?, "
                            + "effective_date = ?, status = ?, notes = ?, updated_at = now() where organization_id = ? and id = ?",
                    code, name, type, request.gradeStep() == null ? current.gradeStep() : clean(request.gradeStep()), amount(request.coefficient(), current.coefficient()),
                    amounts.min, amounts.max, amounts.base, amounts.title, amounts.performance, amounts.concurrent, amounts.gasoline,
                    amounts.other, amounts.total, effectiveDate, status, request.notes() == null ? current.notes() : clean(request.notes()), organizationId, id);
            return get(organizationId, id);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "salary_regulation_code_exists", "Mã quy chế đã tồn tại trong doanh nghiệp.");
        }
    }

    @Transactional
    public void delete(UUID organizationId, UUID id, UUID actorId, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actorId, root);
        if (jdbc.update("delete from " + table + " where organization_id = ? and id = ?", organizationId, id) == 0) {
            throw notFound();
        }
    }

    private SalaryRegulationController.SalaryRegulationView get(UUID organizationId, UUID id) {
        return jdbc.query("select id, organization_id, rule_code, name, salary_type, grade_step, coefficient, min_amount, max_amount, "
                        + "base_salary, title_salary, performance_salary, concurrent_allowance, gasoline_allowance, other_allowance, "
                        + "total_salary, effective_date, status, notes, created_by, created_at, updated_at from " + table + " "
                        + "where organization_id = ? and id = ?", this::view, organizationId, id)
                .stream().findFirst().orElseThrow(SalaryRegulationService::notFound);
    }

    private SalaryRegulationController.SalaryRegulationView view(ResultSet rs, int rowNum) throws SQLException {
        return new SalaryRegulationController.SalaryRegulationView(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getString("rule_code"),
                rs.getString("name"), rs.getString("salary_type"), rs.getString("grade_step"), rs.getBigDecimal("coefficient"),
                rs.getBigDecimal("min_amount"), rs.getBigDecimal("max_amount"), rs.getBigDecimal("base_salary"),
                rs.getBigDecimal("title_salary"), rs.getBigDecimal("performance_salary"), rs.getBigDecimal("concurrent_allowance"),
                rs.getBigDecimal("gasoline_allowance"), rs.getBigDecimal("other_allowance"), rs.getBigDecimal("total_salary"),
                rs.getObject("effective_date", LocalDate.class), rs.getString("status"), rs.getString("notes"),
                rs.getObject("created_by", UUID.class), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private Amounts amounts(SalaryRegulationController.SalaryRegulationRequest request) {
        return amounts(request.baseSalary(), request.titleSalary(), request.performanceSalary(), request.concurrentAllowance(),
                request.gasolineAllowance(), request.otherAllowance(), request.minAmount(), request.maxAmount());
    }

    private Amounts amounts(SalaryRegulationController.SalaryRegulationPatch request,
                            SalaryRegulationController.SalaryRegulationView current) {
        return amounts(value(request.baseSalary(), current.baseSalary()), value(request.titleSalary(), current.titleSalary()),
                value(request.performanceSalary(), current.performanceSalary()), value(request.concurrentAllowance(), current.concurrentAllowance()),
                value(request.gasolineAllowance(), current.gasolineAllowance()), value(request.otherAllowance(), current.otherAllowance()),
                value(request.minAmount(), current.minAmount()), value(request.maxAmount(), current.maxAmount()));
    }

    private Amounts amounts(BigDecimal base, BigDecimal title, BigDecimal performance, BigDecimal concurrent,
                            BigDecimal gasoline, BigDecimal other, BigDecimal min, BigDecimal max) {
        BigDecimal safeMin = amount(min);
        BigDecimal safeMax = amount(max);
        if (safeMax.compareTo(safeMin) < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_salary_range", "Mức tối đa phải lớn hơn hoặc bằng mức tối thiểu.");
        BigDecimal safeBase = amount(base), safeTitle = amount(title), safePerformance = amount(performance);
        BigDecimal safeConcurrent = amount(concurrent), safeGasoline = amount(gasoline), safeOther = amount(other);
        return new Amounts(safeMin, safeMax, safeBase, safeTitle, safePerformance, safeConcurrent, safeGasoline, safeOther,
                safeBase.add(safeTitle).add(safePerformance).add(safeConcurrent).add(safeGasoline).add(safeOther));
    }

    private static BigDecimal value(BigDecimal value, BigDecimal fallback) { return value == null ? fallback : value; }
    private static BigDecimal amount(BigDecimal value) {
        if (value == null) return ZERO;
        if (value.compareTo(ZERO) < 0 || value.precision() > 19) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_salary_amount", "Mức lương và phụ cấp không hợp lệ.");
        return value;
    }
    private static BigDecimal amount(BigDecimal value, BigDecimal fallback) { return amount(value == null ? fallback : value); }
    private static String required(String value, String field) {
        String result = clean(value);
        if (result.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "required_salary_field", field + " là bắt buộc.");
        return result;
    }
    private static String requiredCode(String value) { return required(value, "Mã quy chế").toUpperCase(Locale.ROOT); }
    private static LocalDate requiredDate(LocalDate value) { if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "effective_date_required", "Ngày hiệu lực là bắt buộc."); return value; }
    private static String status(String value) {
        String result = clean(value).toUpperCase(Locale.ROOT);
        if (result.isEmpty()) return "DRAFT";
        if (!List.of("DRAFT", "ACTIVE", "EXPIRED").contains(result)) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_salary_status", "Trạng thái quy chế không hợp lệ.");
        return result;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String like(String value) { return "%" + value + "%"; }
    private static void requirePage(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "Tham số phân trang không hợp lệ."); }
    private static int pages(long total, int size) { return (int) Math.max(1, Math.ceil((double) total / size)); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "salary_regulation_not_found", "Quy chế lương không còn tồn tại hoặc đã bị xóa."); }

    private record Amounts(BigDecimal min, BigDecimal max, BigDecimal base, BigDecimal title, BigDecimal performance,
                           BigDecimal concurrent, BigDecimal gasoline, BigDecimal other, BigDecimal total) {}
}
