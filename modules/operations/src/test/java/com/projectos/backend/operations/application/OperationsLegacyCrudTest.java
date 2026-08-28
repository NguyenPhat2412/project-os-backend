package com.projectos.backend.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import com.projectos.backend.platform.organization.OrganizationDirectory;

class OperationsLegacyCrudTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final ObjectProvider<OrganizationDirectory> organizations = Mockito.mock(ObjectProvider.class);
    private final OperationsApplicationService service = new OperationsApplicationService(jdbc, organizations);

    @Test
    void listsAnEmptyOffboardingCollectionWithoutUsingFallbackData() {
        UUID organizationId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        var result = service.list(organizationId, "offboarding", 0, 100, "", "", null, true);

        assertThat(result.data()).isEmpty();
        assertThat(result.meta().total()).isZero();
    }

    @Test
    void createsOffboardingRecordAndReturnsTheStoredContract() {
        UUID organizationId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", recordId, "organization_id", organizationId, "employee_id", employeeId,
                        "code", "OFF-001", "employee_code", "EMP-01", "employee_name", "Employee",
                        "department", "Engineering", "position", "Developer", "status", "PENDING",
                        "checklist", "{}")));

        Map<String, Object> result = service.create(organizationId, "offboarding", Map.of(
                "employeeId", employeeId.toString(), "employeeCode", "EMP-01", "employeeName", "Employee",
                "resignationDate", "2026-08-25", "lastWorkingDate", "2026-09-25",
                "reasonType", "PERSONAL_REASON", "reasonDetail", "Career change"), null, true);

        assertThat(result).containsEntry("id", recordId).containsEntry("status", "PENDING");
    }

    @Test
    void updatesAndDeletesOffboardingRecordWithinOrganizationScope() {
        UUID organizationId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", recordId, "organization_id", organizationId, "status", "HANDOVER", "checklist", "{}")));

        Map<String, Object> result = service.update(organizationId, "offboarding", recordId.toString(), Map.of(
                "status", "HANDOVER", "checklist", Map.of("taskHandover", true)), null, true);
        service.delete(organizationId, "offboarding", recordId.toString(), null, true);

        assertThat(result).containsEntry("id", recordId).containsEntry("status", "HANDOVER");
        Mockito.verify(jdbc, Mockito.atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    void createsLegacyContractUsingTenantVerifiedEmployeeReference() {
        UUID organizationId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        String id = UUID.randomUUID().toString();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(Map.of("id", employeeId, "employee_uuid", employeeId, "code", "EMP-01", "full_name", "Employee", "department", "Engineering", "position", "Developer")),
                List.of(Map.of("id", id, "employee_uuid", employeeId, "employee_code", "EMP-01", "status", "ACTIVE")));

        Map<String, Object> result = service.create(organizationId, "contracts", Map.of(
                "id", id,
                "employeeId", employeeId.toString(),
                "contractCode", "C-01",
                "contractType", "FULL_TIME",
                "effectiveDate", "2026-01-01"), null, true);

        assertThat(result).containsEntry("id", id).containsEntry("employeeUuid", employeeId);
    }

    @Test
    void queuesContractWarningReminderInTheOutbox() {
        UUID organizationId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("contract_code", "C-01", "employee_name", "Employee", "recipient_email", "employee@example.test")));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.queueContractWarningReminder(organizationId, "contract-01", null, true);

        assertThat(result).containsEntry("contractId", "contract-01").containsEntry("status", "queued");
    }

    @Test
    void createsLegacyTeamWithStringCompatibleIdentifier() {
        UUID organizationId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", "team-01", "organization_uuid", organizationId, "code", "TEAM-01", "name", "Team")));

        Map<String, Object> result = service.create(organizationId, "teams", Map.of(
                "id", "team-01", "code", "TEAM-01", "name", "Team", "slug", "team-01"), null, true);

        assertThat(result).containsEntry("id", "team-01").containsEntry("organizationUuid", organizationId);
    }

    @Test
    void createsGlobalMasterCatalogItemThroughAuthenticatedOperationsScope() {
        UUID organizationId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", "province-hn", "category", "PROVINCE", "code", "HN", "name", "Ha Noi",
                        "display_order", 1, "is_active", true)));

        Map<String, Object> result = service.create(organizationId, "master-data", Map.of(
                "id", "province-hn", "category", "PROVINCE", "code", "HN", "name", "Ha Noi",
                "displayOrder", 1, "isActive", true), null, true);

        assertThat(result).containsEntry("id", "province-hn").containsEntry("category", "PROVINCE");
    }

    @Test
    void createsOrganizationBranchWithTenantScope() {
        UUID organizationId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", UUID.randomUUID(), "organization_id", organizationId, "code", "HN-01",
                        "name", "Ha Noi", "address", "Ha Noi", "status", "ACTIVE")));

        Map<String, Object> result = service.create(organizationId, "branches", Map.of(
                "code", "HN-01", "name", "Ha Noi", "address", "Ha Noi"), null, true);

        assertThat(result).containsEntry("organizationId", organizationId).containsEntry("code", "HN-01");
    }

    @Test
    void employeeCannotReadGlobalMasterDataThroughOrganizationScopedApi() {
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OrganizationDirectory directory = Mockito.mock(OrganizationDirectory.class);
        when(directory.access(organizationId, actorId))
                .thenReturn(new OrganizationDirectory.Access("Asia/Ho_Chi_Minh", "EMPLOYEE"));
        when(organizations.getIfAvailable()).thenReturn(directory);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.list(organizationId, "master-data", 0, 100, "", "", actorId, false))
                .isInstanceOf(com.projectos.backend.platform.api.ApiException.class)
                .hasMessageContaining("Root admin access is required");
    }
}
