package com.projectos.backend.monolith;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MonolithStartupTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
        registry.add("app.internal-token", () -> "test-internal-token");
        registry.add("app.internal-service-token", () -> "test-internal-token");
        registry.add("app.rbac.internal-token", () -> "test-internal-token");
        registry.add("app.outbox.internal-token", () -> "test-internal-token");
        registry.add("app.storage.access-key", () -> "test-access-key");
        registry.add("app.storage.secret-key", () -> "test-secret-key");
        registry.add("app.storage.endpoint", () -> "http://localhost:19000");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
        registry.add("app.security.openapi-public", () -> "true");
        registry.add("spring.data.redis.repositories.enabled", () -> "false");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void startsAgainstCanonicalPublicSchema() {
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'",
                Integer.class)).isEqualTo(65);
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success",
                Integer.class)).isEqualTo(34);
        assertThat(jdbc.queryForObject(
                "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                String.class)).isEqualTo("34");
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_name = 'organization_permissions'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('organization_positions', 'training_courses', "
                        + "'company_regulations', 'company_email_accounts', 'report_definitions', 'organization_branches', "
                        + "'notification_categories')",
                Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' "
                        + "and table_name = 'employees' and column_name in ('code', 'phone', 'notes')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name = 'organization_settings'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void swaggerDocumentsPublicMonolithApiWithoutInternalRoutes() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.info.title").value("Project OS API"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations/{organizationId}/ai/models']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/internal/activities']").doesNotExist());
    }

    @Test
    void performanceScoringSchemaIsTenantSafeAndStartsEmpty() {
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('performance_scoring_rules', 'employee_score_events')",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_constraint where conname in "
                        + "('performance_scoring_rules_code_uq', 'employee_score_events_employee_org_fk', "
                        + "'employee_score_events_rule_org_fk', 'employee_score_events_points_ck')",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "select count(*) from public.performance_scoring_rules",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from public.employee_score_events",
                Integer.class)).isZero();
    }

    @Test
    void everyPublicTableHasExactlyOnePrimaryKey() {
        Integer invalidTables = jdbc.queryForObject("""
                select count(*)
                from (
                    select c.oid
                    from pg_class c
                    join pg_namespace n on n.oid = c.relnamespace
                    left join pg_constraint p on p.conrelid = c.oid and p.contype = 'p'
                    where n.nspname = 'public' and c.relkind = 'r'
                    group by c.oid
                    having count(p.oid) <> 1
                ) invalid
                """, Integer.class);
        assertThat(invalidTables).isZero();
    }

    @Test
    void requiredForeignKeysArePresentAndValidated() {
        Integer unvalidatedForeignKeys = jdbc.queryForObject("""
                select count(*)
                from pg_constraint c
                join pg_namespace n on n.oid = c.connamespace
                where n.nspname = 'public' and c.contype = 'f' and not c.convalidated
                """, Integer.class);
        assertThat(unvalidatedForeignKeys).isZero();
        Integer requiredForeignKeys = jdbc.queryForObject("""
                select count(*)
                from pg_constraint c
                where c.conname in (
                    'employees_organization_fk', 'employees_department_same_organization_fk',
                    'projects_organization_fk', 'resource_records_project_fk',
                    'attendance_records_employee_same_organization_fk',
                    'leave_requests_employee_same_organization_fk',
                    'organization_permissions_organization_fk'
                )
                """, Integer.class);
        assertThat(requiredForeignKeys).isEqualTo(7);
    }

    @Test
    void flywayHistoryAndCanonicalSchemaAreClean() {
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("public");
        assertThat(jdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where not success",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*)
                from (
                    select version
                    from public.flyway_schema_history
                    where version is not null
                    group by version
                    having count(*) > 1
                ) duplicate_versions
                """, Integer.class)).isZero();
    }

    @Test
    void schemaHardeningHasNoDuplicateForeignKeysAndProtectsUnownedReferences() {
        Integer duplicateForeignKeyPairs = jdbc.queryForObject("""
                select count(*)
                from (
                    select conrelid, confrelid, conkey, confkey
                    from pg_constraint
                    where contype = 'f'
                    group by conrelid, confrelid, conkey, confkey
                    having count(*) > 1
                ) duplicates
                """, Integer.class);
        assertThat(duplicateForeignKeyPairs).isZero();

        Integer requiredSchemaHardeningForeignKeys = jdbc.queryForObject("""
                select count(*)
                from pg_constraint
                where conname in (
                    'activity_events_actor_fk',
                    'activity_events_organization_fk',
                    'activity_events_project_fk',
                    'company_policies_organization_fk',
                    'departments_parent_fk',
                    'employee_compensations_employee_same_organization_fk',
                    'permission_group_members_group_same_organization_fk',
                    'user_profiles_user_fk'
                )
                """, Integer.class);
        assertThat(requiredSchemaHardeningForeignKeys).isEqualTo(8);
    }

    @Test
    void finalSchemaGateHasExpectedForeignKeyCountAndAppendOnlyAuditTables() {
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                        + "where n.nspname = 'public' and c.contype = 'f' and c.convalidated",
                Integer.class)).isEqualTo(114);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_trigger t join pg_class c on c.oid = t.tgrelid "
                        + "join pg_namespace n on n.oid = c.relnamespace "
                        + "where n.nspname = 'public' and not t.tgisinternal "
                        + "and t.tgname in ('activity_events_append_only_trigger', 'organization_audit_logs_append_only_trigger')",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_constraint where conname in "
                        + "('attendance_records_location_bounds_ck', 'attendance_records_work_mode_ck', "
                        + "'projects_organization_required_ck', 'outbox_events_attempts_nonnegative_ck') "
                        + "and convalidated",
                Integer.class)).isEqualTo(4);
    }

    @Test
    void foreignKeyColumnsAreIndexedForStableJoins() {
        assertThat(jdbc.queryForObject("""
                with foreign_keys as (
                    select c.conrelid, c.conkey
                    from pg_constraint c
                    join pg_namespace n on n.oid = c.connamespace
                    where n.nspname = 'public' and c.contype = 'f'
                )
                select count(*)
                from foreign_keys f
                where not exists (
                    select 1
                    from pg_index i
                    where i.indrelid = f.conrelid
                      and i.indisvalid
                      and i.indisready
                      and (
                          select array_agg(attnum order by ord)
                          from unnest(i.indkey::int2[]) with ordinality as indexed_columns(attnum, ord)
                          where ord <= cardinality(f.conkey)
                      ) = f.conkey
                )
                """, Integer.class)).isZero();
    }

    @Test
    void knownTenantReferencesHaveNoOrphansOrCrossOrganizationMismatches() {
        assertThat(jdbc.queryForObject("""
                select count(*)
                from employees e
                left join organizations o on o.id = e.organization_id
                where o.id is null
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*)
                from employees e
                join departments d on d.id = e.department_id
                where d.organization_id <> e.organization_id
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*)
                from attendance_records a
                join employees e on e.id = a.employee_id
                where e.organization_id <> a.organization_id
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*)
                from schedule_assignments a
                join employees e on e.id = a.employee_id
                where e.organization_id <> a.organization_id
                """, Integer.class)).isZero();
    }

    @Test
    void legacyUuidReferenceColumnsExistAndRemainSeparateFromBusinessCodes() {
        Integer normalizedColumns = jdbc.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and (
                    (table_name = 'enterprise_contracts' and column_name = 'employee_uuid')
                    or (table_name = 'enterprise_contracts' and column_name = 'organization_uuid')
                    or (table_name = 'enterprise_kpi_evaluations' and column_name = 'employee_uuid')
                    or (table_name = 'enterprise_kpi_evaluations' and column_name = 'organization_uuid')
                    or (table_name = 'enterprise_leave_balances' and column_name = 'employee_uuid')
                    or (table_name = 'enterprise_leave_balances' and column_name = 'organization_uuid')
                    or (table_name = 'enterprise_teams' and column_name = 'organization_uuid')
                    or (table_name = 'enterprise_teams' and column_name = 'department_uuid')
                  )
                """, Integer.class);
        assertThat(normalizedColumns).isEqualTo(8);
    }

    @Test
    void legacyEmployeeReferencesAreTenantSafe() {
        assertThat(jdbc.queryForObject("""
                select count(*)
                from pg_constraint
                where conname in (
                    'enterprise_contracts_employee_org_fk',
                    'enterprise_kpi_evaluations_employee_org_fk',
                    'enterprise_leave_balances_employee_org_fk'
                ) and convalidated
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name in ('enterprise_contracts', 'enterprise_kpi_evaluations', 'enterprise_leave_balances')
                  and column_name = 'organization_uuid'
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                select count(*)
                from enterprise_contracts c
                join employees e on e.id = c.employee_uuid
                where c.organization_uuid is distinct from e.organization_id
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*)
                from enterprise_kpi_evaluations k
                join employees e on e.id = k.employee_uuid
                where k.organization_uuid is distinct from e.organization_id
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*)
                from enterprise_leave_balances b
                join employees e on e.id = b.employee_uuid
                where b.organization_uuid is distinct from e.organization_id
                """, Integer.class)).isZero();
    }

    @Test
    void operationsContractsUseTheCanonicalOrganizationRoute() throws Exception {
        mvc.perform(get("/api/v1/organizations/" + java.util.UUID.randomUUID() + "/contracts")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token -> token.claim("uid", java.util.UUID.randomUUID().toString())
                                        .claim("role", "ROOT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta").exists());
    }

    @Test
    void authenticatedClientsCannotCallInternalRoutes() throws Exception {
        mvc.perform(get("/api/v1/internal/activities")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token -> token.claim("uid", java.util.UUID.randomUUID().toString())
                                        .claim("role", "ROOT_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeDirectoryDoesNotReturnSoftDeletedRows() throws Exception {
        UUID rootId = UUID.randomUUID();
        jdbc.update("insert into public.users (id, display_name, email, role, status) values (?, ?, ?, ?, ?)",
                rootId, "Directory Test Root", rootId + "@test.invalid", "ROOT_ADMIN", "ACTIVE");
        var root = SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.claim("uid", rootId.toString()).claim("role", "ROOT_ADMIN"));
        String organizationId = mvc.perform(post("/api/v1/organizations").with(root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Directory Visibility Org\",\"slug\":\"directory-visibility-org\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        jdbc.update("insert into public.employees (id, organization_id, code, full_name, email, status, is_deleted, deleted_at) "
                        + "values (?, ?::uuid, ?, ?, ?, 'ACTIVE', true, now())",
                UUID.randomUUID(), organizationId, "DELETED-001", "Deleted Directory Employee", "deleted-directory@test.invalid");

        mvc.perform(get("/api/v1/organizations/" + organizationId + "/employees").with(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void operationsDomainCrudUsesPersistedPublicData() throws Exception {
        UUID rootId = java.util.UUID.randomUUID();
        jdbc.update("insert into public.users (id, display_name, email, role, status) values (?, ?, ?, ?, ?)",
                rootId, "Contract Test Root", rootId + "@test.invalid", "ROOT_ADMIN", "ACTIVE");
        var root = SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.claim("uid", rootId.toString()).claim("role", "ROOT_ADMIN"));
        String organizationId = mvc.perform(post("/api/v1/organizations").with(root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operations Contract Org\",\"slug\":\"operations-contract-org\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        String base = "/api/v1/organizations/" + organizationId;
        UUID employeeId = UUID.randomUUID();
        String employeeCode = "EMP-" + employeeId.toString().substring(0, 8);
        jdbc.update("insert into public.employees (id, organization_id, code, full_name, email, status) values (?, ?::uuid, ?, ?, ?, 'ACTIVE')",
                employeeId, organizationId, employeeCode, "Operations Employee", employeeId + "@test.invalid");
        String contractId = UUID.randomUUID().toString();
        mvc.perform(post(base + "/contracts").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + contractId + "\",\"employeeId\":\"" + employeeId + "\",\"contractCode\":\"CTR-01\",\"contractType\":\"FULL_TIME\",\"effectiveDate\":\"2026-01-01\",\"baseSalary\":1000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(contractId));
        mvc.perform(patch(base + "/contracts/" + contractId).with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TERMINATED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("TERMINATED"));
        mvc.perform(delete(base + "/contracts/" + contractId).with(root)).andExpect(status().isNoContent());
        mvc.perform(post(base + "/teams").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"team-01\",\"code\":\"TEAM-01\",\"name\":\"Operations Team\",\"slug\":\"operations-team\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("team-01"));
        mvc.perform(delete(base + "/teams/team-01").with(root)).andExpect(status().isNoContent());
        String trainingId = mvc.perform(post(base + "/training").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"TRN-01\",\"name\":\"Security Fundamentals\",\"status\":\"PLANNED\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.name").value("Security Fundamentals"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mvc.perform(patch(base + "/training/" + trainingId).with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(get(base + "/training").with(root)).andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));

        String regulationId = mvc.perform(post(base + "/regulations").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"REG-01\",\"title\":\"Safety Rule\",\"category\":\"safety\",\"description\":\"Use protective equipment\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mvc.perform(delete(base + "/regulations/" + regulationId).with(root)).andExpect(status().isNoContent());

        String mailboxId = mvc.perform(post(base + "/company-emails").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddress\":\"security@example.test\",\"displayName\":\"Security\",\"mailboxType\":\"PERSONAL\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mvc.perform(delete(base + "/company-emails/" + mailboxId).with(root)).andExpect(status().isNoContent());

        mvc.perform(post(base + "/reports").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Security Report\",\"category\":\"Báo cáo nhân sự\",\"period\":\"Tùy chọn\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void companyEmailAccountReturnsAssignedEmployeeIdentityFromDirectory() throws Exception {
        UUID rootId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        jdbc.update("insert into public.users (id, display_name, email, role, status) values (?, ?, ?, ?, ?)",
                rootId, "Email Contract Root", rootId + "@test.invalid", "ROOT_ADMIN", "ACTIVE");
        var root = SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.claim("uid", rootId.toString()).claim("role", "ROOT_ADMIN"));
        String organizationId = mvc.perform(post("/api/v1/organizations").with(root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Email Contract Org\",\"slug\":\"email-contract-org\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        String employeeCode = "EMP-EMAIL-01";
        jdbc.update("insert into public.employees (id, organization_id, code, full_name, email, status) values (?, ?::uuid, ?, ?, ?, 'ACTIVE')",
                employeeId, organizationId, employeeCode, "Email Employee", "email.employee@test.invalid");

        String base = "/api/v1/organizations/" + organizationId;
        String mailboxId = mvc.perform(post(base + "/company-emails").with(root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddress\":\"employee@example.test\",\"displayName\":\"Email Employee\","
                                + "\"assignedEmployeeId\":\"" + employeeId + "\",\"mailboxType\":\"PERSONAL\","
                                + "\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assignedEmployeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.data.employeeCode").value(employeeCode))
                .andExpect(jsonPath("$.data.employeeName").value("Email Employee"))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        mvc.perform(get(base + "/company-emails").with(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employeeCode").value(employeeCode))
                .andExpect(jsonPath("$.data[0].employeeName").value("Email Employee"));
        mvc.perform(delete(base + "/company-emails/" + mailboxId).with(root))
                .andExpect(status().isNoContent());
    }

    @Test
    void performanceScoringContractPersistsRulesEventsAndSummary() throws Exception {
        UUID rootId = UUID.randomUUID();
        jdbc.update("insert into public.users (id, display_name, email, role, status) values (?, ?, ?, ?, ?)",
                rootId, "Scoring Contract Root", rootId + "@test.invalid", "ROOT_ADMIN", "ACTIVE");
        var root = SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.claim("uid", rootId.toString()).claim("role", "ROOT_ADMIN"));
        String organizationId = mvc.perform(post("/api/v1/organizations").with(root)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Scoring Contract Org\",\"slug\":\"scoring-contract-org\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        String base = "/api/v1/organizations/" + organizationId;
        UUID employeeId = UUID.randomUUID();
        jdbc.update("insert into public.employees (id, organization_id, code, full_name, email, status) values (?, ?::uuid, ?, ?, ?, 'ACTIVE')",
                employeeId, organizationId, "EMP-SCORE-01", "Scoring Employee", employeeId + "@test.invalid");

        mvc.perform(post(base + "/scoring-rules").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"ON_TIME_CHECKIN\",\"name\":\"Đúng giờ\",\"description\":\"Chấm công đúng giờ\",\"category\":\"ATTENDANCE\",\"points\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ruleCode").value("ON_TIME_CHECKIN"));
        mvc.perform(get(base + "/scoring-rules").with(root))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));

        String event = "attendance:EMP-SCORE-01:2026-08-25";
        mvc.perform(post(base + "/scoring-rules/points").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeCode\":\"EMP-SCORE-01\",\"ruleCode\":\"ON_TIME_CHECKIN\",\"eventKey\":\"" + event + "\",\"reason\":\"Đúng giờ\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pointsAwarded").value(5)).andExpect(jsonPath("$.data.created").value(true));
        mvc.perform(post(base + "/scoring-rules/points").with(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeCode\":\"EMP-SCORE-01\",\"ruleCode\":\"ON_TIME_CHECKIN\",\"eventKey\":\"" + event + "\",\"reason\":\"Đúng giờ\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pointsAwarded").value(5)).andExpect(jsonPath("$.data.created").value(false));
        assertThat(jdbc.queryForObject("select count(*) from public.employee_score_events where organization_id = ?::uuid", Integer.class, organizationId)).isEqualTo(1);
        mvc.perform(get(base + "/score-summary?employeeCode=EMP-SCORE-01").with(root))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalPoints").value(5)).andExpect(jsonPath("$.data.tier").value("Đang phát triển"));
    }
}
