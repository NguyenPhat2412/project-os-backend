package com.projectos.backend.organization.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.projectos.backend.platform.api.ApiException;

@ExtendWith(MockitoExtension.class)
class OrganizationPermissionServiceTest {
    @Mock OrganizationPermissionRepository permissions;
    @Mock OrganizationMembershipRepository memberships;
    @Mock WorkspaceCache cache;
    @Mock OrganizationAuditService audit;
    @Mock PermissionGroupService permissionGroups;

    private OrganizationPermissionService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationPermissionService(permissions, memberships, cache, audit, permissionGroups);
    }

    @Test
    void deniesAiFallbackWhenAssignedPermissionGroupsDoNotIncludeAi() {
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(memberships.findByOrganizationIdAndUserId(organizationId, actorId))
                .thenReturn(Optional.of(new OrganizationMembership(organizationId, actorId, OrganizationMembership.Role.MEMBER)));
        when(permissions.findByOrganizationId(organizationId)).thenReturn(List.of());
        when(permissionGroups.assignedModules(organizationId, actorId)).thenReturn(Optional.of(Set.of("profile")));

        assertThatThrownBy(() -> service.requirePermission(organizationId, actorId, false, "page:ai"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("feature_permission_denied");
    }
}
