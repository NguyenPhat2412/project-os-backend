package com.projectos.backend.organization.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.organization.OrganizationPermissionPort;

@Service
public class OrganizationPermissionService implements OrganizationPermissionPort {
    private static final Set<String> ROLES = Set.of("SUPER_ADMIN", "HR_MANAGER", "DEPT_LEAD", "EMPLOYEE", "INTERN");
    private final OrganizationPermissionRepository permissions;
    private final OrganizationMembershipRepository memberships;
    private final WorkspaceCache cache;
    private final OrganizationAuditService audit;
    private final PermissionGroupService permissionGroups;

    OrganizationPermissionService(OrganizationPermissionRepository permissions,
                                   OrganizationMembershipRepository memberships, WorkspaceCache cache,
                                   OrganizationAuditService audit, PermissionGroupService permissionGroups) {
        this.permissions = permissions;
        this.memberships = memberships;
        this.cache = cache;
        this.audit = audit;
        this.permissionGroups = permissionGroups;
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> list(UUID organizationId, UUID actorId, boolean root) {
        requireAdmin(organizationId, actorId, root);
        Map<String, List<String>> result = new LinkedHashMap<>();
        permissions.findByOrganizationId(organizationId).forEach(value ->
                result.computeIfAbsent(value.getPermissionKey(), ignored -> new java.util.ArrayList<>()).add(value.getRoleKey()));
        result.values().forEach(value -> value.sort(String::compareTo));
        return result;
    }

    @Transactional
    public Map<String, List<String>> replace(UUID organizationId, Map<String, List<String>> requested,
                                             UUID actorId, boolean root) {
        requireAdmin(organizationId, actorId, root);
        if (requested == null) throw new ApiException(HttpStatus.BAD_REQUEST, "permissions_required", "Permissions are required");
        Map<String, List<String>> normalized = normalize(requested);
        Set<String> namespaces = normalized.keySet().stream()
                .map(this::namespaceOf)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<OrganizationPermission> next = new java.util.ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        normalized.forEach((permissionKey, roles) -> {
            if (roles == null) return;
            roles.forEach(role -> {
                String roleKey = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
                if (!ROLES.contains(roleKey)) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_role", "Invalid permission role");
                if (seen.add(permissionKey + "\u0000" + roleKey)) next.add(new OrganizationPermission(organizationId, permissionKey, roleKey));
            });
        });
        namespaces.forEach(namespace -> permissions.deleteByOrganizationIdAndPermissionKeyStartingWith(organizationId, namespace + ":"));
        // A reset can contain the same role that already exists. Flush the deletes
        // before scheduling replacement inserts so PostgreSQL's unique constraint
        // is not evaluated against the old row in the same transaction.
        permissions.flush();
        permissions.saveAll(next);
        audit.record(organizationId, actorId, "organization_permissions_replaced", "organization_permissions", null,
                null, normalized, null);
        cache.invalidateOrganization(organizationId);
        return list(organizationId, actorId, root);
    }

    private Map<String, List<String>> normalize(Map<String, List<String>> requested) {
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        requested.forEach((key, roles) -> {
            String permissionKey = cleanKey(key);
            if (permissionKey.startsWith("page:") || permissionKey.startsWith("component:") || permissionKey.startsWith("module:")) {
                normalized.put(permissionKey, roles);
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_permission_namespace",
                        "Permission keys must use page:, component:, or module: namespace");
            }
        });
        return normalized;
    }

    private String namespaceOf(String permissionKey) {
        int separator = permissionKey.indexOf(':');
        return separator > 0 ? permissionKey.substring(0, separator) : permissionKey;
    }

    @Transactional(readOnly = true)
    public Set<String> modulesForRole(UUID organizationId, String roleKey) {
        String normalized = roleKey == null ? "" : roleKey.toUpperCase(Locale.ROOT);
        Set<String> modules = new LinkedHashSet<>();
        permissions.findByOrganizationId(organizationId).stream()
                .filter(value -> normalized.equals(value.getRoleKey()))
                .map(OrganizationPermission::getPermissionKey)
                .filter(value -> value.startsWith("module:") || value.startsWith("page:"))
                .map(value -> value.substring(value.indexOf(':') + 1))
                .forEach(modules::add);
        return Set.copyOf(modules);
    }

    @Transactional(readOnly = true)
    @Override
    public void requirePermission(UUID organizationId, UUID actorId, boolean root, String permissionKey) {
        if (root) return;
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, actorId)
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied"));
        String roleKey = switch (membership.getRole()) {
            case OWNER, ADMIN -> "SUPER_ADMIN";
            case HR -> "HR_MANAGER";
            case DEPARTMENT_MANAGER -> "DEPT_LEAD";
            case EMPLOYEE, MEMBER -> "EMPLOYEE";
        };
        List<OrganizationPermission> configured = permissions.findByOrganizationId(organizationId);
        boolean hasExplicitRule = configured.stream().anyMatch(value -> permissionKey.equals(value.getPermissionKey()));
        boolean allowed = configured.stream().anyMatch(value -> permissionKey.equals(value.getPermissionKey())
                && roleKey.equals(value.getRoleKey()));
        if (!hasExplicitRule) {
            boolean aiGroupDenied = !root
                    && permissionGroups.assignedModules(organizationId, actorId)
                    .map(modules -> !modules.contains("ai"))
                    .orElse(false)
                    && (permissionKey.equals("page:ai") || permissionKey.startsWith("component:ai:"));
            if (aiGroupDenied) {
                throw new ApiException(HttpStatus.FORBIDDEN, "feature_permission_denied",
                        "Bạn không có quyền thực hiện thao tác này.");
            }
            allowed = switch (permissionKey) {
                case "page:kpi-thi-dua" -> roleKey.equals("SUPER_ADMIN") || roleKey.equals("HR_MANAGER")
                        || roleKey.equals("DEPT_LEAD") || roleKey.equals("EMPLOYEE");
                case "module:kpi-thi-dua.rules" -> roleKey.equals("SUPER_ADMIN") || roleKey.equals("HR_MANAGER");
                case "module:kpi-thi-dua.points" -> roleKey.equals("SUPER_ADMIN") || roleKey.equals("HR_MANAGER");
                case "EMAIL_TEMPLATE_READ", "EMAIL_CAMPAIGN_READ" -> roleKey.equals("SUPER_ADMIN")
                        || roleKey.equals("HR_MANAGER") || roleKey.equals("DEPT_LEAD")
                        || roleKey.equals("EMPLOYEE");
                case "EMAIL_TEMPLATE_MANAGE", "EMAIL_CAMPAIGN_CREATE", "EMAIL_CAMPAIGN_QUEUE",
                        "EMAIL_CAMPAIGN_CANCEL", "EMAIL_CAMPAIGN_RETRY" -> roleKey.equals("SUPER_ADMIN")
                        || roleKey.equals("HR_MANAGER");
                case "EMAIL_CAMPAIGN_PREVIEW" -> roleKey.equals("SUPER_ADMIN")
                        || roleKey.equals("HR_MANAGER") || roleKey.equals("DEPT_LEAD");
                case "page:ai", "component:ai:website-guide", "component:ai:self-read",
                        "component:ai:attendance-self-read", "component:ai:project-read",
                        "component:ai:knowledge-read" -> true;
                case "component:ai:organization-read", "component:ai:employee-read",
                        "component:ai:attendance-team-read", "component:ai:report-read" ->
                        roleKey.equals("SUPER_ADMIN") || roleKey.equals("HR_MANAGER") || roleKey.equals("DEPT_LEAD");
                case "component:ai:model-read" -> roleKey.equals("SUPER_ADMIN") || roleKey.equals("HR_MANAGER");
                case "component:ai:provider-test" -> roleKey.equals("SUPER_ADMIN");
                default -> false;
            };
        }
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "feature_permission_denied", "Bạn không có quyền thực hiện thao tác này.");
        }
    }

    public void requireOrganizationAdmin(UUID organizationId, UUID actorId, boolean root) {
        requireAdmin(organizationId, actorId, root);
    }

    public void requireOrganizationMember(UUID organizationId, UUID actorId, boolean root) {
        if (root) return;
        memberships.findByOrganizationIdAndUserId(organizationId, actorId)
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Bạn không có quyền truy cập doanh nghiệp này."));
    }

    private String cleanKey(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > 180) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_permission_key", "Invalid permission key");
        }
        return result;
    }

    private void requireAdmin(UUID organizationId, UUID actorId, boolean root) {
        if (root) return;
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, actorId)
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_admin_required", "Organization admin access is required"));
        if (membership.getRole() != OrganizationMembership.Role.OWNER && membership.getRole() != OrganizationMembership.Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "organization_admin_required", "Organization admin access is required");
        }
    }
}
