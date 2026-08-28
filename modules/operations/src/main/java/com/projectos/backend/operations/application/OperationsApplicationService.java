package com.projectos.backend.operations.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.Collection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.api.PageResponse;
import com.projectos.backend.platform.organization.OrganizationDirectory;
import com.projectos.backend.operations.web.OperationsMutationRequest;
import com.projectos.backend.operations.web.ContractWarningReminderDto;
import com.projectos.backend.operations.web.OperationsResourceDto;
import com.projectos.backend.operations.web.OperationsResourceMapper;

/** Read port for the verified legacy HRM tables still present in public schema. */
@Service
public class OperationsApplicationService {
    private static final java.util.Set<String> HR_RESOURCES = java.util.Set.of(
            "contracts", "contract-warnings", "kpi", "leave", "leave-balances", "offboarding",
            "training", "regulations", "company-emails", "reports", "teams", "branches");
    private final JdbcTemplate jdbc;
    private final ObjectProvider<OrganizationDirectory> organizations;
    private final ObjectMapper json = new ObjectMapper();
    private final OperationsResourceMapper mapper;

    public OperationsApplicationService(JdbcTemplate jdbc, ObjectProvider<OrganizationDirectory> organizations) {
        this(jdbc, organizations, new OperationsResourceMapper());
    }

    @Autowired
    public OperationsApplicationService(JdbcTemplate jdbc, ObjectProvider<OrganizationDirectory> organizations,
                                        OperationsResourceMapper mapper) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.mapper = mapper;
    }

    /** Typed application boundary used by the public controller. */
    @Transactional(readOnly = true)
    public PageResponse<OperationsResourceDto> listDto(UUID organizationId, String resource, int page, int size,
                                                        String search, String category, UUID actorId, boolean root) {
        PageResponse<Map<String, Object>> legacy = list(organizationId, resource, page, size, search, category, actorId, root);
        return new PageResponse<>(legacy.data().stream().map(mapper::toDto).toList(), legacy.meta());
    }

    /** Typed application boundary used by the public controller. */
    @Transactional
    public OperationsResourceDto createDto(UUID organizationId, String resource, OperationsMutationRequest payload,
                                           UUID actorId, boolean root) {
        return mapper.toDto(create(organizationId, resource, payload, actorId, root));
    }

    /** Typed application boundary used by the public controller. */
    @Transactional
    public OperationsResourceDto updateDto(UUID organizationId, String resource, String id,
                                           OperationsMutationRequest payload, UUID actorId, boolean root) {
        Map<String, Object> values = json.convertValue(payload, new TypeReference<Map<String, Object>>() {});
        return mapper.toDto(update(organizationId, resource, id, values, actorId, root));
    }

    /** Typed application boundary used by the public controller. */
    @Transactional
    public ContractWarningReminderDto queueContractWarningReminderDto(UUID organizationId, String contractId,
                                                                       UUID actorId, boolean root) {
        Map<String, Object> result = queueContractWarningReminder(organizationId, contractId, actorId, root);
        return new ContractWarningReminderDto(
                (UUID) result.get("id"),
                String.valueOf(result.get("contractId")),
                String.valueOf(result.get("status")));
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(UUID organizationId, String resource, int page, int size,
                                                  String search, String category, UUID actorId, boolean root) {
        if (page < 0 || size < 1 || size > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "Invalid pagination");
        }
        requireOperationsAccess(organizationId, resource, actorId, root);
        Query query = queryFor(resource, organizationId, category);
        List<Map<String, Object>> rows = query.parameters().isEmpty()
                ? jdbc.queryForList(query.sql())
                : jdbc.queryForList(query.sql(), query.parameters().toArray());
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> filtered = normalizedSearch.isBlank() ? rows : rows.stream()
                .filter(row -> row.values().stream().anyMatch(value -> value != null
                        && String.valueOf(value).toLowerCase(Locale.ROOT).contains(normalizedSearch)))
                .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<Map<String, Object>> data = filtered.subList(from, to).stream().map(this::dto).toList();
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);
        return PageResponse.of(data, page, size, filtered.size(), totalPages);
    }

    @Transactional
    public Map<String, Object> create(UUID organizationId, String resource, OperationsMutationRequest payload,
                                      UUID actorId, boolean root) {
        Map<String, Object> values = json.convertValue(payload, new TypeReference<Map<String, Object>>() {});
        return create(organizationId, resource, values, actorId, root);
    }

    @Transactional
    public Map<String, Object> create(UUID organizationId, String resource, Map<String, Object> payload, UUID actorId, boolean root) {
        requireOperationsAccess(organizationId, resource, actorId, root);
        UUID id = UUID.randomUUID();
        String normalized = normalizeResource(resource);
        switch (normalized) {
            case "training" -> jdbc.update("insert into public.training_courses (id, organization_id, course_code, name, category, instructor, start_date, end_date, location, sessions_count, attendees_count, cost, status, notes, created_by, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, organizationId, required(payload, "courseCode", "course code"), required(payload, "name", "name"), text(payload, "category"), text(payload, "instructor", "trainer"), timestamp(payload, "startDate"), timestamp(payload, "endDate"), text(payload, "location"), integer(payload, "sessions"), integer(payload, "attendeesCount"), decimal(payload, "cost"), enumValue(payload, "status", "PLANNED"), text(payload, "notes"), actorId, actorId);
            case "regulations" -> jdbc.update("insert into public.company_regulations (id, organization_id, code, title, category, description, penalties, effective_date, status, created_by, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, organizationId, required(payload, "code", "code"), required(payload, "title", "title"), text(payload, "category"), text(payload, "description"), text(payload, "penalties"), date(payload, "effectiveDate"), enumValue(payload, "status", "DRAFT"), actorId, actorId);
            case "company-emails" -> {
                UUID assignedEmployeeId = uuid(payload, "assignedEmployeeId", "employeeId");
                UUID departmentId = uuid(payload, "departmentId");
                if (assignedEmployeeId != null) verifyActiveEmployee(organizationId, assignedEmployeeId);
                if (departmentId != null) verifyDepartment(organizationId, departmentId);
                jdbc.update("insert into public.company_email_accounts (id, organization_id, email_address, display_name, assigned_employee_id, department_id, mailbox_type, storage_quota_mb, status, aliases, forward_to, notes, created_by, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, organizationId, required(payload, "emailAddress", "email address"), required(payload, "displayName", "display name"), assignedEmployeeId, departmentId, enumValue(payload, "mailboxType", "PERSONAL"), integerOrZero(payload, "quotaTotalMb", "storageQuotaMb"), enumValue(payload, "status", "ACTIVE"), aliases(payload.get("aliases")), text(payload, "forwardTo"), text(payload, "notes"), actorId, actorId);
            }
            case "reports" -> jdbc.update("insert into public.report_definitions (id, organization_id, name, category, period, start_date, end_date, department_id, employee_filter, notes, created_by, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, organizationId, required(payload, "name", "name"), text(payload, "category"), text(payload, "period"), date(payload, "startDate"), date(payload, "endDate"), uuid(payload, "departmentId"), text(payload, "employees", "employeeFilter"), text(payload, "notes"), actorId, actorId);
            case "contracts" -> createContract(organizationId, payload);
            case "kpi" -> createKpi(organizationId, payload);
            case "leave", "leave-balances" -> createLeaveBalance(organizationId, payload);
            case "offboarding" -> createOffboarding(organizationId, payload, id, actorId);
            case "teams" -> createTeam(organizationId, payload);
            case "master-data" -> createMasterData(payload, id);
            case "branches" -> createBranch(organizationId, payload, id);
            default -> throw unsupported(normalized);
        }
        String createdId = switch (normalized) {
            case "contracts", "kpi", "leave", "leave-balances", "teams", "master-data", "branches" -> legacyId(payload, id);
            default -> id.toString();
        };
        return findOne(normalized, organizationId, createdId);
    }

    @Transactional
    public Map<String, Object> update(UUID organizationId, String resource, String id, Map<String, Object> payload, UUID actorId, boolean root) {
        requireOperationsAccess(organizationId, resource, actorId, root);
        String normalized = normalizeResource(resource);
        int changed = switch (normalized) {
            case "training" -> jdbc.update("update public.training_courses set course_code=coalesce(?, course_code), name=coalesce(?, name), category=coalesce(?, category), instructor=coalesce(?, instructor), start_date=coalesce(?, start_date), end_date=coalesce(?, end_date), location=coalesce(?, location), sessions_count=coalesce(?, sessions_count), attendees_count=coalesce(?, attendees_count), cost=coalesce(?, cost), status=coalesce(?, status), notes=coalesce(?, notes), updated_by=?, updated_at=now() where organization_id=? and id=?", requiredOrNull(payload, "courseCode"), text(payload, "name"), text(payload, "category"), text(payload, "instructor", "trainer"), timestamp(payload, "startDate"), timestamp(payload, "endDate"), text(payload, "location"), integer(payload, "sessions"), integer(payload, "attendeesCount"), decimal(payload, "cost"), enumValue(payload, "status", null), text(payload, "notes"), actorId, organizationId, uuidId(id));
            case "regulations" -> jdbc.update("update public.company_regulations set code=coalesce(?, code), title=coalesce(?, title), category=coalesce(?, category), description=coalesce(?, description), penalties=coalesce(?, penalties), effective_date=coalesce(?, effective_date), status=coalesce(?, status), updated_by=?, updated_at=now() where organization_id=? and id=?", requiredOrNull(payload, "code"), text(payload, "title"), text(payload, "category"), text(payload, "description"), text(payload, "penalties"), date(payload, "effectiveDate"), enumValue(payload, "status", null), actorId, organizationId, uuidId(id));
            case "company-emails" -> {
                UUID assignedEmployeeId = uuid(payload, "assignedEmployeeId", "employeeId");
                UUID departmentId = uuid(payload, "departmentId");
                if (assignedEmployeeId != null) verifyActiveEmployee(organizationId, assignedEmployeeId);
                if (departmentId != null) verifyDepartment(organizationId, departmentId);
                yield jdbc.update("update public.company_email_accounts set email_address=coalesce(?, email_address), display_name=coalesce(?, display_name), assigned_employee_id=coalesce(?, assigned_employee_id), department_id=coalesce(?, department_id), mailbox_type=coalesce(?, mailbox_type), storage_quota_mb=coalesce(?, storage_quota_mb), status=coalesce(?, status), forward_to=coalesce(?, forward_to), notes=coalesce(?, notes), updated_by=?, updated_at=now() where organization_id=? and id=?", requiredOrNull(payload, "emailAddress"), text(payload, "displayName"), assignedEmployeeId, departmentId, enumValue(payload, "mailboxType", null), integer(payload, "quotaTotalMb", "storageQuotaMb"), enumValue(payload, "status", null), text(payload, "forwardTo"), text(payload, "notes"), actorId, organizationId, uuidId(id));
            }
            case "reports" -> jdbc.update("update public.report_definitions set name=coalesce(?, name), category=coalesce(?, category), period=coalesce(?, period), start_date=coalesce(?, start_date), end_date=coalesce(?, end_date), department_id=coalesce(?, department_id), employee_filter=coalesce(?, employee_filter), notes=coalesce(?, notes), updated_by=?, updated_at=now() where organization_id=? and id=?", text(payload, "name"), text(payload, "category"), text(payload, "period"), date(payload, "startDate"), date(payload, "endDate"), uuid(payload, "departmentId"), text(payload, "employees", "employeeFilter"), text(payload, "notes"), actorId, organizationId, uuidId(id));
            case "contracts" -> updateContract(organizationId, id, payload);
            case "kpi" -> updateKpi(organizationId, id, payload);
            case "leave", "leave-balances" -> updateLeaveBalance(organizationId, id, payload);
            case "offboarding" -> updateOffboarding(organizationId, id, payload, actorId);
            case "teams" -> updateTeam(organizationId, id, payload);
            case "master-data" -> updateMasterData(id, payload);
            case "branches" -> updateBranch(organizationId, id, payload);
            default -> throw unsupported(normalized);
        };
        if (changed == 0) throw new ApiException(HttpStatus.NOT_FOUND, "operations_record_not_found", "Operations record not found");
        return findOne(normalized, organizationId, id);
    }

    @Transactional
    public Map<String, Object> queueContractWarningReminder(UUID organizationId, String contractId,
                                                              UUID actorId, boolean root) {
        requireOperationsAccess(organizationId, "contract-warnings", actorId, root);
        List<Map<String, Object>> rows = jdbc.queryForList("select c.contract_code, c.employee_name, coalesce(e.email, '') as recipient_email "
                + "from public.enterprise_contracts c "
                + "join public.employees e on e.id = c.employee_uuid and e.organization_id = ? "
                + "where c.id = ?", organizationId, contractId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "contract_warning_not_found", "Contract warning was not found");
        }
        Map<String, Object> row = rows.getFirst();
        UUID eventId = UUID.randomUUID();
        int queued = jdbc.update("insert into public.outbox_events "
                + "(id, event_type, payload, attempts, next_attempt_at, created_at) "
                + "values (?, 'contract_warning_email_requested', jsonb_build_object(" 
                + "'organizationId', ?, 'contractId', ?, 'contractCode', ?, 'employeeName', ?, "
                + "'recipientEmail', ?, 'requestedBy', ?), 0, now(), now())",
                eventId, organizationId, contractId, text(row, "contract_code"), text(row, "employee_name"),
                text(row, "recipient_email"), actorId);
        if (queued != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "contract_warning_reminder_failed",
                    "Contract reminder could not be queued");
        }
        return Map.of("id", eventId, "contractId", contractId, "status", "queued");
    }

    public Map<String, Object> update(UUID organizationId, String resource, UUID id, Map<String, Object> payload,
                                      UUID actorId, boolean root) {
        return update(organizationId, resource, id.toString(), payload, actorId, root);
    }

    @Transactional
    public void delete(UUID organizationId, String resource, String id, UUID actorId, boolean root) {
        requireOperationsAccess(organizationId, resource, actorId, root);
        String table = switch (normalizeResource(resource)) {
            case "training" -> "training_courses";
            case "regulations" -> "company_regulations";
            case "company-emails" -> "company_email_accounts";
            case "reports" -> "report_definitions";
            case "contracts" -> "enterprise_contracts";
            case "kpi" -> "enterprise_kpi_evaluations";
            case "leave", "leave-balances" -> "enterprise_leave_balances";
            case "offboarding" -> "offboarding_records";
            case "teams" -> "enterprise_teams";
            case "master-data" -> "enterprise_master_catalogs";
            case "branches" -> "organization_branches";
            default -> throw unsupported(resource);
        };
        int changed = switch (normalizeResource(resource)) {
            case "contracts", "kpi", "leave", "leave-balances" -> jdbc.update("delete from public." + table + " where id=? and employee_uuid in (select id from public.employees where organization_id=?)", id, organizationId);
            case "offboarding" -> jdbc.update("delete from public." + table + " where id=? and organization_id=?", uuidId(id), organizationId);
            case "teams" -> jdbc.update("delete from public." + table + " where organization_uuid=? and id=?", organizationId, id);
            case "master-data" -> jdbc.update("delete from public." + table + " where id=?", id);
            case "branches" -> jdbc.update("delete from public." + table + " where organization_id=? and id=?", organizationId, uuidId(id));
            default -> jdbc.update("delete from public." + table + " where organization_id=? and id=?", organizationId, UUID.fromString(id));
        };
        if (changed == 0) throw new ApiException(HttpStatus.NOT_FOUND, "operations_record_not_found", "Operations record not found");
    }

    public void delete(UUID organizationId, String resource, UUID id, UUID actorId, boolean root) {
        delete(organizationId, resource, id.toString(), actorId, root);
    }

    private Query queryFor(String resource, UUID organizationId, String category) {
        return switch (normalizeResource(resource)) {
            case "contracts" -> new Query("select " + projection("contracts", "c") + " from public.enterprise_contracts c "
                    + "where exists (select 1 from public.employees e where e.organization_id = ? "
                    + "and e.id = c.employee_uuid) order by c.updated_at desc", List.of(organizationId));
            case "contract-warnings" -> new Query("select warning.id, warning.contract_code, warning.employee_code, warning.employee_name, warning.department, warning.position, "
                    + "warning.contract_type, warning.effective_date, warning.expire_date, warning.base_salary, warning.allowances, warning.performance_bonus, "
                    + "warning.status, warning.email, warning.phone, warning.manager_name, (warning.expiry_date - current_date) as warning_days_remaining "
                    + "from (select c.id, c.contract_code, c.employee_code, c.employee_name, "
                    + "coalesce(c.department, e.department) as department, coalesce(c.\"position\", e.\"position\") as \"position\", "
                    + "c.contract_type, c.effective_date, c.expire_date, c.base_salary, c.allowances, c.performance_bonus, "
                    + "c.status, e.email, e.phone, cast(null as varchar) as manager_name, "
                    + "case when c.expire_date ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$' then to_date(c.expire_date, 'DD/MM/YYYY') "
                    + "when c.expire_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' then to_date(c.expire_date, 'YYYY-MM-DD') end as expiry_date "
                    + "from public.enterprise_contracts c join public.employees e on e.id = c.employee_uuid and e.organization_id = ?) warning "
                    + "where warning.expiry_date is not null and warning.expiry_date <= current_date + 60 "
                    + "and coalesce(warning.status, 'ACTIVE') <> 'TERMINATED' order by warning.expiry_date asc", List.of(organizationId));
            case "kpi" -> new Query("select " + projection("kpi", "k") + " from public.enterprise_kpi_evaluations k "
                    + "where exists (select 1 from public.employees e where e.organization_id = ? "
                    + "and e.id = k.employee_uuid) order by k.updated_at desc", List.of(organizationId));
            case "leave", "leave-balances" -> new Query("select " + projection("leave-balances", "b") + " from public.enterprise_leave_balances b "
                    + "where exists (select 1 from public.employees e where e.organization_id = ? "
                    + "and e.id = b.employee_uuid) order by b.updated_at desc", List.of(organizationId));
            case "offboarding" -> new Query("select " + projection("offboarding", "o") + " from public.offboarding_records o "
                    + "join public.employees e on e.id = o.employee_id and e.organization_id = ? "
                    + "where o.organization_id = ? order by o.updated_at desc", List.of(organizationId, organizationId));
            case "teams" -> new Query("select " + projection("teams", "t") + " from public.enterprise_teams t where organization_uuid = ? "
                    + "order by updated_at desc", List.of(organizationId));
            case "branches" -> new Query("select " + projection("branches", "b") + " from public.organization_branches b where organization_id = ? "
                    + "order by updated_at desc", List.of(organizationId));
            case "master-data" -> {
                String normalizedCategory = category == null ? "" : category.trim();
                if (normalizedCategory.isBlank()) {
                    yield new Query("select " + projection("master-data", "m") + " from public.enterprise_master_catalogs m "
                            + "where is_active = true order by category, display_order, name", List.of());
                }
                yield new Query("select " + projection("master-data", "m") + " from public.enterprise_master_catalogs m "
                        + "where is_active = true and category = ? order by display_order, name",
                        List.of(normalizedCategory.toUpperCase(Locale.ROOT)));
            }
            case "company" -> new Query("select " + projection("company", "c") + " from public.enterprise_company_profile c order by updated_at desc", List.of());
            case "training" -> new Query("select " + projection("training", "t") + " from public.training_courses t where organization_id = ? order by updated_at desc", List.of(organizationId));
            case "regulations" -> new Query("select " + projection("regulations", "r") + " from public.company_regulations r where organization_id = ? order by updated_at desc", List.of(organizationId));
            case "company-emails" -> new Query("select " + projection("company-emails", "m") + " from public.company_email_accounts m "
                    + "left join public.employees e on e.id = m.assigned_employee_id and e.organization_id = m.organization_id "
                    + "left join public.departments d on d.id = m.department_id and d.organization_id = m.organization_id "
                    + "where m.organization_id = ? order by m.updated_at desc", List.of(organizationId));
            case "reports" -> new Query("select " + projection("reports", "r") + " from public.report_definitions r where organization_id = ? order by updated_at desc", List.of(organizationId));
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "operations_resource_not_found",
                    "This operations resource has no verified public-schema owner");
        };
    }

    private Map<String, Object> dto(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(camelCase(key), normalizeJdbcValue(value)));
        Object warningDays = result.get("warningDaysRemaining");
        if (warningDays instanceof Number number) {
            result.put("urgency", ContractWarningPolicy.urgency(number.intValue()));
        }
        return result;
    }

    private Object normalizeJdbcValue(Object value) {
        if (value != null && "org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            try {
                String type = String.valueOf(value.getClass().getMethod("getType").invoke(value));
                String content = String.valueOf(value.getClass().getMethod("getValue").invoke(value));
                if ("jsonb".equalsIgnoreCase(type)) return json.readValue(content, Object.class);
                return content;
            } catch (Exception exception) {
                return value.toString();
            }
        }
        if (value instanceof java.sql.Array array) {
            try { return array.getArray(); } catch (java.sql.SQLException exception) { return null; }
        }
        return value;
    }

    private String camelCase(String key) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : key.toCharArray()) {
            if (character == '_') { upper = true; continue; }
            result.append(upper ? Character.toUpperCase(character) : Character.toLowerCase(character));
            upper = false;
        }
        return result.toString();
    }

    private void requireOrganizationAccess(UUID organizationId, UUID actorId, boolean root) {
        if (root) return;
        OrganizationDirectory directory = organizations.getIfAvailable();
        if (directory == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_directory_unavailable",
                    "Organization directory is unavailable");
        }
        try {
            OrganizationDirectory.Access access = directory.access(organizationId, actorId);
            if (access == null || access.role() == null || access.role().isBlank()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_directory_unavailable",
                    "Organization directory is unavailable");
        }
    }

    private void requireOperationsAccess(UUID organizationId, String resource, UUID actorId, boolean root) {
        if (root) return;
        requireOrganizationAccess(organizationId, actorId, false);
        String normalized = normalizeResource(resource);
        if ("master-data".equals(normalized) || "company".equals(normalized)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "root_admin_required",
                    "Root admin access is required for global operations data");
        }
        if (HR_RESOURCES.contains(normalized)) {
            OrganizationDirectory directory = organizations.getIfAvailable();
            if (directory == null) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_directory_unavailable",
                        "Organization directory is unavailable");
            }
            OrganizationDirectory.Access access;
            try {
                access = directory.access(organizationId, actorId);
            } catch (Exception exception) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_directory_unavailable",
                        "Organization directory is unavailable");
            }
            String role = access == null || access.role() == null ? "" : access.role().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("OWNER", "ADMIN", "HR", "SUPER_ADMIN", "PLATFORM_ADMIN", "HR_MANAGER").contains(role)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "hr_access_required",
                        "HR or organization admin access is required");
            }
        }
    }

    private record Query(String sql, List<Object> parameters) {}

    private String normalizeResource(String resource) { return resource == null ? "" : resource.trim().toLowerCase(Locale.ROOT); }
    private ApiException unsupported(String resource) { return new ApiException(HttpStatus.NOT_FOUND, "operations_resource_not_found", "This operations resource has no verified public-schema owner: " + resource); }
    private String required(Map<String, Object> payload, String key, String label) { String value = text(payload, key); if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "missing_" + key, label + " is required"); return value.trim(); }
    private String requiredOrNull(Map<String, Object> payload, String key) { String value = text(payload, key); return value == null || value.isBlank() ? null : value.trim(); }
    private String text(Map<String, Object> payload, String... keys) { for (String key : keys) { Object value = payload.get(key); if (value != null) return String.valueOf(value).trim(); } return null; }
    private UUID uuid(Map<String, Object> payload, String... keys) { String value = text(payload, keys); return value == null || value.isBlank() ? null : parseUuid(value, keys[0]); }
    private UUID parseUuid(String value, String field) { try { return UUID.fromString(value); } catch (IllegalArgumentException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + field, field + " must be a UUID"); } }
    private Integer integer(Map<String, Object> payload, String... keys) { String value = text(payload, keys); return value == null || value.isBlank() ? null : parseInteger(value, keys[0]); }
    private Integer integerOrZero(Map<String, Object> payload, String... keys) { return integer(payload, keys) == null ? 0 : integer(payload, keys); }
    private Integer parseInteger(String value, String field) { try { int parsed = Integer.parseInt(value); if (parsed < 0) throw new NumberFormatException(); return parsed; } catch (NumberFormatException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + field, field + " must be a non-negative integer"); } }
    private java.math.BigDecimal decimal(Map<String, Object> payload, String key) { String value = text(payload, key); if (value == null || value.isBlank()) return null; try { var parsed = new java.math.BigDecimal(value); if (parsed.signum() < 0) throw new NumberFormatException(); return parsed; } catch (NumberFormatException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + key, key + " must be a non-negative number"); } }
    private String enumValue(Map<String, Object> payload, String key, String defaultValue) { String value = text(payload, key); String result = value == null || value.isBlank() ? defaultValue : value; return result == null ? null : result.toUpperCase(Locale.ROOT); }
    private Timestamp timestamp(Map<String, Object> payload, String key) { String value = text(payload, key); if (value == null || value.isBlank()) return null; try { return Timestamp.from(Instant.parse(value)); } catch (Exception ignored) { try { return Timestamp.from(LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant()); } catch (Exception exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + key, key + " must be an ISO date or timestamp"); } } }
    private java.sql.Date date(Map<String, Object> payload, String key) { String value = text(payload, key); if (value == null || value.isBlank()) return null; try { return java.sql.Date.valueOf(LocalDate.parse(value)); } catch (Exception exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + key, key + " must be an ISO date"); } }
    private java.sql.Date requiredDate(Map<String, Object> payload, String key) {
        java.sql.Date value = date(payload, key);
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "missing_" + key, key + " is required");
        return value;
    }
    private String offboardingReason(Map<String, Object> payload, String key, boolean required) {
        String value = text(payload, key);
        if (value == null || value.isBlank()) {
            if (required) throw new ApiException(HttpStatus.BAD_REQUEST, "missing_" + key, key + " is required");
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PERSONAL_REASON", "HEALTH_FAMILY", "RELOCATION", "CONTRACT_EXPIRATION",
                    "MUTUAL_AGREEMENT", "RETIREMENT", "OTHER" -> normalized;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + key, key + " is not supported");
        };
    }
    private String offboardingStatus(Map<String, Object> payload, String key, String defaultValue) {
        String value = text(payload, key);
        if (value == null || value.isBlank()) return defaultValue;
        String normalized = value.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING", "HANDOVER", "SETTLEMENT", "COMPLETED", "REJECTED" -> normalized;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + key, key + " is not supported");
        };
    }
    private String jsonb(Object value) {
        if (value == null) return "{\"taskHandover\":false,\"assetsHandover\":false,\"financeSettlement\":false,\"accountRevocation\":false}";
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_checklist", "checklist must be valid JSON"); }
    }
    private String jsonbOrNull(Object value) { return value == null ? null : jsonb(value); }
    private String[] aliases(Object raw) { if (raw == null) return new String[0]; if (raw instanceof Collection<?> values) return values.stream().map(String::valueOf).toArray(String[]::new); return new String[] { String.valueOf(raw) }; }
    private Integer integerOrDefault(Map<String, Object> payload, String key, int fallback) { Integer value = integer(payload, key); return value == null ? fallback : value; }
    private Boolean booleanValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Boolean booleanResult) return booleanResult;
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) return true;
        if ("false".equals(normalized)) return false;
        throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + key, key + " must be a boolean");
    }
    private boolean booleanOrDefault(Map<String, Object> payload, String key, boolean fallback) {
        Boolean value = booleanValue(payload, key);
        return value == null ? fallback : value;
    }
    private Integer positiveInteger(Map<String, Object> payload, String key) {
        Integer value = integer(payload, key);
        if (value != null && value < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + key, key + " must be greater than zero");
        return value;
    }
    private int positiveIntegerOrDefault(Map<String, Object> payload, String key, int fallback) { Integer value = positiveInteger(payload, key); return value == null ? fallback : value; }
    private String textOrDefault(Map<String, Object> payload, String key, String fallback) { String value = text(payload, key); return value == null || value.isBlank() ? fallback : value; }
    private java.math.BigDecimal coordinate(Map<String, Object> payload, String key) {
        String value = text(payload, key);
        if (value == null || value.isBlank()) return null;
        try { return new java.math.BigDecimal(value); }
        catch (NumberFormatException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + key, key + " must be a number"); }
    }

    private int createMasterData(Map<String, Object> payload, UUID generatedId) {
        return jdbc.update("insert into public.enterprise_master_catalogs (id, category, code, name, display_order, is_active) values (?, ?, ?, ?, ?, ?)",
                legacyId(payload, generatedId), required(payload, "category", "category"), required(payload, "code", "code"),
                required(payload, "name", "name"), integerOrDefault(payload, "displayOrder", 1), booleanOrDefault(payload, "isActive", true));
    }

    private int updateMasterData(String id, Map<String, Object> payload) {
        return jdbc.update("update public.enterprise_master_catalogs set category=coalesce(?, category), code=coalesce(?, code), name=coalesce(?, name), display_order=coalesce(?, display_order), is_active=coalesce(?, is_active) where id=?",
                requiredOrNull(payload, "category"), requiredOrNull(payload, "code"), requiredOrNull(payload, "name"),
                integer(payload, "displayOrder"), booleanValue(payload, "isActive"), id);
    }

    private int createBranch(UUID organizationId, Map<String, Object> payload, UUID generatedId) {
        UUID branchId = uuid(payload, "id");
        return jdbc.update("insert into public.organization_branches (id, organization_id, code, name, branch_type, address, phone, email, manager_name, employees_count, gps_latitude, gps_longitude, gps_radius_meters, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                branchId == null ? generatedId : branchId, organizationId, required(payload, "code", "branch code"),
                required(payload, "name", "branch name"), textOrDefault(payload, "branchType", "BRANCH"),
                required(payload, "address", "branch address"), text(payload, "phone"), text(payload, "email"),
                text(payload, "managerName"), integerOrDefault(payload, "employeesCount", 0), coordinate(payload, "gpsLatitude"),
                coordinate(payload, "gpsLongitude"), positiveIntegerOrDefault(payload, "gpsRadiusMeters", 250),
                enumValue(payload, "status", "ACTIVE"));
    }

    private int updateBranch(UUID organizationId, String id, Map<String, Object> payload) {
        return jdbc.update("update public.organization_branches set code=coalesce(?, code), name=coalesce(?, name), branch_type=coalesce(?, branch_type), address=coalesce(?, address), phone=coalesce(?, phone), email=coalesce(?, email), manager_name=coalesce(?, manager_name), employees_count=coalesce(?, employees_count), gps_latitude=coalesce(?, gps_latitude), gps_longitude=coalesce(?, gps_longitude), gps_radius_meters=coalesce(?, gps_radius_meters), status=coalesce(?, status), updated_at=now() where organization_id=? and id=?",
                requiredOrNull(payload, "code"), requiredOrNull(payload, "name"), text(payload, "branchType"), requiredOrNull(payload, "address"),
                text(payload, "phone"), text(payload, "email"), text(payload, "managerName"), integer(payload, "employeesCount"),
                coordinate(payload, "gpsLatitude"), coordinate(payload, "gpsLongitude"), positiveInteger(payload, "gpsRadiusMeters"),
                enumValue(payload, "status", null), organizationId, uuidId(id));
    }

    private int createContract(UUID organizationId, Map<String, Object> payload) {
        LegacyEmployee employee = employee(organizationId, payload);
        return jdbc.update("insert into public.enterprise_contracts (id, employee_id, employee_uuid, organization_uuid, employee_code, employee_name, department, \"position\", contract_code, contract_type, sign_date, effective_date, expire_date, base_salary, allowances, performance_bonus, status, warning_days_remaining) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                legacyId(payload, UUID.randomUUID()), employee.id().toString(), employee.id(), employee.organizationId(), employee.code(), employee.name(), employee.department(), employee.position(),
                required(payload, "contractCode", "contract code"), required(payload, "contractType", "contract type"), text(payload, "signDate"),
                required(payload, "effectiveDate", "effective date"), text(payload, "expireDate"), decimalOrZero(payload, "baseSalary"), decimalOrZero(payload, "allowances"),
                decimalOrZero(payload, "performanceBonus"), enumValue(payload, "status", "ACTIVE"), integer(payload, "warningDaysRemaining"));
    }

    private int createKpi(UUID organizationId, Map<String, Object> payload) {
        LegacyEmployee employee = employee(organizationId, payload);
        return jdbc.update("insert into public.enterprise_kpi_evaluations (id, employee_id, employee_uuid, organization_uuid, employee_code, employee_name, department, period, target_title, weight_percent, target_metric, actual_metric, score_percent, ranking, ranking_label, evaluator_name, evaluation_date, notes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                legacyId(payload, UUID.randomUUID()), employee.id().toString(), employee.id(), employee.organizationId(), employee.code(), employee.name(), employee.department(), required(payload, "period", "period"),
                required(payload, "targetTitle", "target title"), decimalOr(payload, "weightPercent", java.math.BigDecimal.valueOf(100)), text(payload, "targetMetric"), text(payload, "actualMetric"),
                decimalOr(payload, "scorePercent", java.math.BigDecimal.valueOf(100)), enumValue(payload, "ranking", "EXCELLENT"), text(payload, "rankingLabel"), text(payload, "evaluatorName"),
                text(payload, "evaluationDate"), text(payload, "notes"));
    }

    private int createLeaveBalance(UUID organizationId, Map<String, Object> payload) {
        LegacyEmployee employee = employee(organizationId, payload);
        java.math.BigDecimal standard = decimalOr(payload, "standardQuota", java.math.BigDecimal.valueOf(12));
        java.math.BigDecimal seniority = decimalOr(payload, "seniorityBonus", java.math.BigDecimal.ZERO);
        java.math.BigDecimal carried = decimalOr(payload, "carriedOver", java.math.BigDecimal.ZERO);
        java.math.BigDecimal entitled = decimalOr(payload, "totalEntitled", standard.add(seniority).add(carried));
        java.math.BigDecimal used = decimalOr(payload, "usedDays", java.math.BigDecimal.ZERO);
        return jdbc.update("insert into public.enterprise_leave_balances (id, employee_uuid, organization_uuid, employee_code, employee_name, department, standard_quota, seniority_bonus, carried_over, total_entitled, used_days, remaining_days, year) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                legacyId(payload, UUID.randomUUID()), employee.id(), employee.organizationId(), employee.code(), employee.name(), employee.department(), standard, seniority, carried, entitled, used,
                decimalOr(payload, "remainingDays", entitled.subtract(used)), payload.containsKey("year") ? integer(payload, "year") : LocalDate.now().getYear());
    }

    private int createOffboarding(UUID organizationId, Map<String, Object> payload, UUID generatedId, UUID actorId) {
        LegacyEmployee employee = employee(organizationId, payload);
        UUID recordId = uuid(payload, "id");
        UUID resolvedId = recordId == null ? generatedId : recordId;
        String code = textOrDefault(payload, "code", "OFF-" + resolvedId.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        return jdbc.update("insert into public.offboarding_records "
                + "(id, organization_id, employee_id, code, employee_code, employee_name, department, \"position\", "
                + "contract_code, hire_date, resignation_date, last_working_date, reason_type, reason_detail, status, "
                + "handover_receiver_name, checklist, unpaid_salary_amount, unused_leave_days, unused_leave_compensation, "
                + "severance_pay, total_settlement_amount, decision_number, decision_date, assets_notes, notes, created_by, updated_by) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                resolvedId, organizationId, employee.id(), code, employee.code(), employee.name(), employee.department(), employee.position(),
                text(payload, "contractCode"), date(payload, "hireDate"), requiredDate(payload, "resignationDate"),
                requiredDate(payload, "lastWorkingDate"), offboardingReason(payload, "reasonType", true),
                required(payload, "reasonDetail", "reason detail"), offboardingStatus(payload, "status", "PENDING"),
                text(payload, "handoverReceiverName"), jsonb(payload.get("checklist")), decimalOrZero(payload, "unpaidSalaryAmount"),
                decimalOrZero(payload, "unusedLeaveDays"), decimalOrZero(payload, "unusedLeaveCompensation"), decimalOrZero(payload, "severancePay"),
                decimalOrZero(payload, "totalSettlementAmount"), text(payload, "decisionNumber"), date(payload, "decisionDate"),
                text(payload, "assetsNotes"), text(payload, "notes"), actorId, actorId);
    }

    private int createTeam(UUID organizationId, Map<String, Object> payload) {
        UUID departmentId = uuid(payload, "departmentId");
        if (departmentId != null) verifyDepartment(organizationId, departmentId);
        UUID leaderId = uuid(payload, "leaderId");
        if (leaderId != null) employeeById(organizationId, leaderId);
        return jdbc.update("insert into public.enterprise_teams (id, organization_id, organization_uuid, department_id, department_uuid, department_name, code, name, slug, leader_id, leader_name, members_count, description, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                legacyId(payload, UUID.randomUUID()), organizationId.toString(), organizationId, departmentId == null ? null : departmentId.toString(), departmentId,
                text(payload, "departmentName"), required(payload, "code", "team code"), required(payload, "name", "team name"),
                required(payload, "slug", "team slug"), leaderId == null ? null : leaderId.toString(), text(payload, "leaderName"), integerOrZero(payload, "membersCount"),
                text(payload, "description"), enumValue(payload, "status", "ACTIVE"));
    }

    private int updateContract(UUID organizationId, String id, Map<String, Object> payload) {
        return jdbc.update("update public.enterprise_contracts set contract_code=coalesce(?, contract_code), contract_type=coalesce(?, contract_type), sign_date=coalesce(?, sign_date), effective_date=coalesce(?, effective_date), expire_date=coalesce(?, expire_date), base_salary=coalesce(?, base_salary), allowances=coalesce(?, allowances), performance_bonus=coalesce(?, performance_bonus), status=coalesce(?, status), warning_days_remaining=coalesce(?, warning_days_remaining), updated_at=now() where id=? and employee_uuid in (select id from public.employees where organization_id=?)",
                text(payload, "contractCode"), text(payload, "contractType"), text(payload, "signDate"), text(payload, "effectiveDate"), text(payload, "expireDate"), decimal(payload, "baseSalary"),
                decimal(payload, "allowances"), decimal(payload, "performanceBonus"), enumValue(payload, "status", null), integer(payload, "warningDaysRemaining"), id, organizationId);
    }

    private int updateKpi(UUID organizationId, String id, Map<String, Object> payload) {
        return jdbc.update("update public.enterprise_kpi_evaluations set period=coalesce(?, period), target_title=coalesce(?, target_title), weight_percent=coalesce(?, weight_percent), target_metric=coalesce(?, target_metric), actual_metric=coalesce(?, actual_metric), score_percent=coalesce(?, score_percent), ranking=coalesce(?, ranking), ranking_label=coalesce(?, ranking_label), evaluator_name=coalesce(?, evaluator_name), evaluation_date=coalesce(?, evaluation_date), notes=coalesce(?, notes), updated_at=now() where id=? and employee_uuid in (select id from public.employees where organization_id=?)",
                text(payload, "period"), text(payload, "targetTitle"), decimal(payload, "weightPercent"), text(payload, "targetMetric"), text(payload, "actualMetric"), decimal(payload, "scorePercent"),
                enumValue(payload, "ranking", null), text(payload, "rankingLabel"), text(payload, "evaluatorName"), text(payload, "evaluationDate"), text(payload, "notes"), id, organizationId);
    }

    private int updateLeaveBalance(UUID organizationId, String id, Map<String, Object> payload) {
        return jdbc.update("update public.enterprise_leave_balances set standard_quota=coalesce(?, standard_quota), seniority_bonus=coalesce(?, seniority_bonus), carried_over=coalesce(?, carried_over), total_entitled=coalesce(?, total_entitled), used_days=coalesce(?, used_days), remaining_days=coalesce(?, remaining_days), year=coalesce(?, year), updated_at=now() where id=? and employee_uuid in (select id from public.employees where organization_id=?)",
                decimal(payload, "standardQuota"), decimal(payload, "seniorityBonus"), decimal(payload, "carriedOver"), decimal(payload, "totalEntitled"), decimal(payload, "usedDays"), decimal(payload, "remainingDays"), integer(payload, "year"), id, organizationId);
    }

    private int updateOffboarding(UUID organizationId, String id, Map<String, Object> payload, UUID actorId) {
        return jdbc.update("update public.offboarding_records set contract_code=coalesce(?, contract_code), resignation_date=coalesce(?, resignation_date), "
                + "last_working_date=coalesce(?, last_working_date), reason_type=coalesce(?, reason_type), reason_detail=coalesce(?, reason_detail), "
                + "status=coalesce(?, status), handover_receiver_name=coalesce(?, handover_receiver_name), checklist=coalesce(?::jsonb, checklist), "
                + "unpaid_salary_amount=coalesce(?, unpaid_salary_amount), unused_leave_days=coalesce(?, unused_leave_days), "
                + "unused_leave_compensation=coalesce(?, unused_leave_compensation), severance_pay=coalesce(?, severance_pay), "
                + "total_settlement_amount=coalesce(?, total_settlement_amount), decision_number=coalesce(?, decision_number), "
                + "decision_date=coalesce(?, decision_date), assets_notes=coalesce(?, assets_notes), notes=coalesce(?, notes), updated_by=?, updated_at=now() "
                + "where organization_id=? and id=?",
                text(payload, "contractCode"), date(payload, "resignationDate"), date(payload, "lastWorkingDate"),
                offboardingReason(payload, "reasonType", false), requiredOrNull(payload, "reasonDetail"),
                offboardingStatus(payload, "status", null), text(payload, "handoverReceiverName"), jsonbOrNull(payload.get("checklist")),
                decimal(payload, "unpaidSalaryAmount"), decimal(payload, "unusedLeaveDays"), decimal(payload, "unusedLeaveCompensation"),
                decimal(payload, "severancePay"), decimal(payload, "totalSettlementAmount"), text(payload, "decisionNumber"),
                date(payload, "decisionDate"), text(payload, "assetsNotes"), text(payload, "notes"), actorId, organizationId, uuidId(id));
    }

    private int updateTeam(UUID organizationId, String id, Map<String, Object> payload) {
        UUID departmentId = uuid(payload, "departmentId");
        if (departmentId != null) verifyDepartment(organizationId, departmentId);
        UUID leaderId = uuid(payload, "leaderId");
        if (leaderId != null) employeeById(organizationId, leaderId);
        return jdbc.update("update public.enterprise_teams set department_id=coalesce(?, department_id), department_uuid=coalesce(?, department_uuid), department_name=coalesce(?, department_name), code=coalesce(?, code), name=coalesce(?, name), slug=coalesce(?, slug), leader_id=coalesce(?, leader_id), leader_name=coalesce(?, leader_name), members_count=coalesce(?, members_count), description=coalesce(?, description), status=coalesce(?, status), updated_at=now() where organization_uuid=? and id=?",
                departmentId == null ? null : departmentId.toString(), departmentId, text(payload, "departmentName"), text(payload, "code"), text(payload, "name"), text(payload, "slug"),
                leaderId == null ? null : leaderId.toString(), text(payload, "leaderName"), integer(payload, "membersCount"), text(payload, "description"), enumValue(payload, "status", null), organizationId, id);
    }

    private LegacyEmployee employee(UUID organizationId, Map<String, Object> payload) {
        UUID employeeId = uuid(payload, "employeeId", "employeeUuid");
        String employeeCode = text(payload, "employeeCode");
        if (employeeId == null && (employeeCode == null || employeeCode.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "employee_reference_required", "employeeId or employeeCode is required");
        }
        String sql = employeeId == null
                ? "select id, code, full_name, department, \"position\" from public.employees where organization_id=? and code=?"
                : "select id, code, full_name, department, \"position\" from public.employees where organization_id=? and id=?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, organizationId, employeeId == null ? employeeCode : employeeId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "employee_not_found", "Employee is not in this organization");
        Map<String, Object> row = rows.getFirst();
        return new LegacyEmployee((UUID) row.get("id"), organizationId, String.valueOf(row.get("code")), String.valueOf(row.get("full_name")), text(row, "department"), text(row, "position"));
    }

    private void employeeById(UUID organizationId, UUID employeeId) { employee(organizationId, Map.of("employeeId", employeeId)); }
    private void verifyActiveEmployee(UUID organizationId, UUID employeeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id from public.employees where id=? and organization_id=? and upper(status)='ACTIVE'", employeeId, organizationId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "employee_not_found", "Employee is not active in this organization");
    }

    private void verifyDepartment(UUID organizationId, UUID departmentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id from public.departments where id=? and organization_id=?", departmentId, organizationId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "department_not_found", "Department is not in this organization");
    }
    private record LegacyEmployee(UUID id, UUID organizationId, String code, String name, String department, String position) {}
    private String legacyId(Map<String, Object> payload, UUID generated) { return text(payload, "id") == null ? generated.toString() : text(payload, "id"); }
    private java.math.BigDecimal decimalOr(Map<String, Object> payload, String key, java.math.BigDecimal fallback) { return decimal(payload, key) == null ? fallback : decimal(payload, key); }
    private java.math.BigDecimal decimalOrZero(Map<String, Object> payload, String key) { return decimalOr(payload, key, java.math.BigDecimal.ZERO); }
    private UUID uuidId(String id) { return parseUuid(id, "id"); }
    private String legacyTable(String resource) { return switch (normalizeResource(resource)) { case "contracts" -> "enterprise_contracts"; case "kpi" -> "enterprise_kpi_evaluations"; case "leave", "leave-balances" -> "enterprise_leave_balances"; default -> throw unsupported(resource); }; }
    private String ownedTable(String resource) { return switch (normalizeResource(resource)) { case "training" -> "training_courses"; case "regulations" -> "company_regulations"; case "company-emails" -> "company_email_accounts"; case "reports" -> "report_definitions"; default -> throw unsupported(resource); }; }

    /** Explicit projections keep the public Operations read contract stable when legacy tables gain columns. */
    private String projection(String resource, String alias) {
        return switch (normalizeResource(resource)) {
            case "contracts" -> alias + ".id, " + alias + ".employee_id, " + alias + ".employee_uuid, " + alias + ".organization_uuid, " + alias + ".employee_code, " + alias + ".employee_name, " + alias + ".department, " + alias + ".\"position\", " + alias + ".contract_code, " + alias + ".contract_type, " + alias + ".sign_date, " + alias + ".effective_date, " + alias + ".expire_date, " + alias + ".base_salary, " + alias + ".allowances, " + alias + ".performance_bonus, " + alias + ".status, " + alias + ".warning_days_remaining, " + alias + ".created_at, " + alias + ".updated_at";
            case "kpi" -> alias + ".id, " + alias + ".employee_id, " + alias + ".employee_uuid, " + alias + ".organization_uuid, " + alias + ".employee_code, " + alias + ".employee_name, " + alias + ".department, " + alias + ".period, " + alias + ".target_title, " + alias + ".weight_percent, " + alias + ".target_metric, " + alias + ".actual_metric, " + alias + ".score_percent, " + alias + ".ranking, " + alias + ".ranking_label, " + alias + ".evaluator_name, " + alias + ".evaluation_date, " + alias + ".notes, " + alias + ".created_at, " + alias + ".updated_at";
            case "leave-balances" -> alias + ".id, " + alias + ".employee_uuid, " + alias + ".organization_uuid, " + alias + ".employee_code, " + alias + ".employee_name, " + alias + ".department, " + alias + ".standard_quota, " + alias + ".seniority_bonus, " + alias + ".carried_over, " + alias + ".total_entitled, " + alias + ".used_days, " + alias + ".remaining_days, " + alias + ".year, " + alias + ".created_at, " + alias + ".updated_at";
            case "offboarding" -> alias + ".id, " + alias + ".organization_id, " + alias + ".employee_id, " + alias + ".code, " + alias + ".employee_code, " + alias + ".employee_name, " + alias + ".department, " + alias + ".\"position\", " + alias + ".contract_code, " + alias + ".hire_date, " + alias + ".resignation_date, " + alias + ".last_working_date, " + alias + ".reason_type, " + alias + ".reason_detail, " + alias + ".status, " + alias + ".handover_receiver_id, " + alias + ".handover_receiver_name, " + alias + ".checklist, " + alias + ".unpaid_salary_amount, " + alias + ".unused_leave_days, " + alias + ".unused_leave_compensation, " + alias + ".severance_pay, " + alias + ".total_settlement_amount, " + alias + ".decision_number, " + alias + ".decision_date, " + alias + ".assets_notes, " + alias + ".notes, " + alias + ".created_by, " + alias + ".updated_by, " + alias + ".created_at, " + alias + ".updated_at";
            case "teams" -> alias + ".id, " + alias + ".organization_id, " + alias + ".organization_uuid, " + alias + ".department_id, " + alias + ".department_uuid, " + alias + ".department_name, " + alias + ".code, " + alias + ".name, " + alias + ".slug, " + alias + ".leader_id, " + alias + ".leader_name, " + alias + ".members_count, " + alias + ".description, " + alias + ".status, " + alias + ".created_at, " + alias + ".updated_at";
            case "master-data" -> alias + ".id, " + alias + ".category, " + alias + ".code, " + alias + ".name, " + alias + ".display_order, " + alias + ".is_active";
            case "branches" -> alias + ".id, " + alias + ".organization_id, " + alias + ".code, " + alias + ".name, " + alias + ".branch_type, " + alias + ".address, " + alias + ".phone, " + alias + ".email, " + alias + ".manager_name, " + alias + ".employees_count, " + alias + ".gps_latitude, " + alias + ".gps_longitude, " + alias + ".gps_radius_meters, " + alias + ".status, " + alias + ".created_at, " + alias + ".updated_at";
            case "company" -> alias + ".id, " + alias + ".company_name, " + alias + ".international_name, " + alias + ".short_name, " + alias + ".tax_code, " + alias + ".established_date, " + alias + ".headquarters, " + alias + ".legal_representative, " + alias + ".representative_title, " + alias + ".phone, " + alias + ".email, " + alias + ".website, " + alias + ".bank_account, " + alias + ".bank_name, " + alias + ".business_license, " + alias + ".registered_capital, " + alias + ".total_employees, " + alias + ".branches, " + alias + ".created_at, " + alias + ".updated_at";
            case "training" -> alias + ".id, " + alias + ".organization_id, " + alias + ".course_code, " + alias + ".name, " + alias + ".category, " + alias + ".instructor, " + alias + ".start_date, " + alias + ".end_date, " + alias + ".location, " + alias + ".sessions_count, " + alias + ".attendees_count, " + alias + ".cost, " + alias + ".status, " + alias + ".notes, " + alias + ".created_by, " + alias + ".updated_by, " + alias + ".created_at, " + alias + ".updated_at";
            case "regulations" -> alias + ".id, " + alias + ".organization_id, " + alias + ".code, " + alias + ".title, " + alias + ".category, " + alias + ".description, " + alias + ".penalties, " + alias + ".effective_date, " + alias + ".status, " + alias + ".created_by, " + alias + ".updated_by, " + alias + ".created_at, " + alias + ".updated_at";
            case "company-emails" -> alias + ".id, " + alias + ".organization_id, " + alias + ".email_address, " + alias + ".display_name, " + alias + ".assigned_employee_id, " + alias + ".department_id, d.name as department, e.code as employee_code, e.full_name as employee_name, " + alias + ".mailbox_type, " + alias + ".storage_quota_mb, " + alias + ".status, " + alias + ".aliases, " + alias + ".forward_to, " + alias + ".notes, " + alias + ".created_by, " + alias + ".updated_by, " + alias + ".created_at, " + alias + ".updated_at";
            case "reports" -> alias + ".id, " + alias + ".organization_id, " + alias + ".name, " + alias + ".category, " + alias + ".period, " + alias + ".start_date, " + alias + ".end_date, " + alias + ".department_id, " + alias + ".employee_filter, " + alias + ".notes, " + alias + ".created_by, " + alias + ".updated_by, " + alias + ".created_at, " + alias + ".updated_at";
            default -> throw unsupported(resource);
        };
    }

    private Map<String, Object> findOne(String resource, UUID organizationId, String id) {
        String normalized = normalizeResource(resource);
        Query query = switch (normalized) {
            case "contracts", "kpi", "leave", "leave-balances" -> new Query("select " + projection(normalized, "r") + " from public." + legacyTable(normalized) + " r"
                    + " where id=? and employee_uuid in (select id from public.employees where organization_id=?)", List.of(id, organizationId));
            case "offboarding" -> new Query("select " + projection("offboarding", "o") + " from public.offboarding_records o "
                    + "join public.employees e on e.id = o.employee_id and e.organization_id = ? "
                    + "where o.organization_id=? and o.id=?", List.of(organizationId, organizationId, uuidId(id)));
            case "teams" -> new Query("select " + projection("teams", "t") + " from public.enterprise_teams t where organization_uuid=? and id=?", List.of(organizationId, id));
            case "master-data" -> new Query("select " + projection("master-data", "m") + " from public.enterprise_master_catalogs m where id=?", List.of(id));
            case "branches" -> new Query("select " + projection("branches", "b") + " from public.organization_branches b where organization_id=? and id=?", List.of(organizationId, uuidId(id)));
            case "company-emails" -> new Query("select " + projection("company-emails", "m") + " from public.company_email_accounts m "
                    + "left join public.employees e on e.id = m.assigned_employee_id and e.organization_id = m.organization_id "
                    + "left join public.departments d on d.id = m.department_id and d.organization_id = m.organization_id "
                    + "where m.organization_id=? and m.id=?", List.of(organizationId, uuidId(id)));
            case "training", "regulations", "reports" -> new Query("select " + projection(normalized, "r") + " from public." + ownedTable(normalized) + " r where organization_id=? and id=?", List.of(organizationId, uuidId(id)));
            default -> throw unsupported(resource);
        };
        List<Map<String, Object>> rows = jdbc.queryForList(query.sql(), query.parameters().toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "operations_record_not_found", "Operations record not found");
        return dto(rows.getFirst());
    }
}
