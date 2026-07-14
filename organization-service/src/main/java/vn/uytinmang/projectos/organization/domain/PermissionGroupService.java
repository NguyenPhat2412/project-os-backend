package vn.uytinmang.projectos.organization.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.organization.web.PermissionGroupController;
import vn.uytinmang.projectos.platform.api.ApiException;

@Service
public class PermissionGroupService {
    public static final Set<String> AVAILABLE_MODULES = Set.of("dashboard", "projects", "tasks", "daily-reports",
            "attendance", "organization", "employees", "project-management", "operations", "knowledge",
            "activity", "reports", "admin", "profile");

    private final PermissionGroupRepository groups;
    private final PermissionGroupMemberRepository groupMembers;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationAuditService audit;
    private final WorkspaceCache cache;

    PermissionGroupService(PermissionGroupRepository groups, PermissionGroupMemberRepository groupMembers,
                           OrganizationMembershipRepository memberships, OrganizationAuditService audit,
                           WorkspaceCache cache) {
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.memberships = memberships;
        this.audit = audit;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<GroupView> list(UUID organizationId, UUID actorId, boolean root) {
        requireAdmin(organizationId, actorId, root);
        return groups.findByOrganizationIdOrderByNameAsc(organizationId).stream().map(this::view).toList();
    }

    @Transactional
    public GroupView create(UUID organizationId, PermissionGroupController.GroupRequest request,
                            UUID actorId, boolean root) {
        requireAdmin(organizationId, actorId, root);
        String name = name(request.name());
        if (groups.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "permission_group_name_exists", "Permission group name exists");
        }
        PermissionGroup group = groups.save(new PermissionGroup(organizationId, name, clean(request.description()),
                modules(request.modules())));
        syncMembers(group, request.memberIds() == null ? Set.of() : request.memberIds());
        audit.record(organizationId, actorId, "permission_group_created", "permission_group", group.getId(),
                null, snapshot(group), null);
        cache.invalidateOrganization(organizationId);
        return view(group);
    }

    @Transactional
    public GroupView update(UUID organizationId, UUID groupId, PermissionGroupController.GroupPatch request,
                            UUID actorId, boolean root) {
        requireAdmin(organizationId, actorId, root);
        PermissionGroup group = requireGroup(organizationId, groupId);
        Map<String, Object> before = snapshot(group);
        String nextName = request.name() == null ? null : name(request.name());
        if (nextName != null && !nextName.equalsIgnoreCase(group.getName())
                && groups.existsByOrganizationIdAndNameIgnoreCase(organizationId, nextName)) {
            throw new ApiException(HttpStatus.CONFLICT, "permission_group_name_exists", "Permission group name exists");
        }
        group.update(nextName, request.description() == null ? null : clean(request.description()),
                request.modules() == null ? null : modules(request.modules()));
        if (request.memberIds() != null) syncMembers(group, request.memberIds());
        audit.record(organizationId, actorId, "permission_group_updated", "permission_group", groupId,
                before, snapshot(group), null);
        cache.invalidateOrganization(organizationId);
        return view(group);
    }

    @Transactional
    public void delete(UUID organizationId, UUID groupId, UUID actorId, boolean root) {
        requireAdmin(organizationId, actorId, root);
        PermissionGroup group = requireGroup(organizationId, groupId);
        Map<String, Object> before = snapshot(group);
        groupMembers.deleteByGroupId(groupId);
        groups.delete(group);
        audit.record(organizationId, actorId, "permission_group_deleted", "permission_group", groupId,
                before, null, null);
        cache.invalidateOrganization(organizationId);
    }

    @Transactional(readOnly = true)
    Optional<Set<String>> assignedModules(UUID organizationId, UUID userId) {
        List<PermissionGroupMember> assignments = groupMembers.findByOrganizationIdAndUserId(organizationId, userId);
        if (assignments.isEmpty()) return Optional.empty();
        Set<UUID> groupIds = assignments.stream().map(PermissionGroupMember::getGroupId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> result = groups.findAllById(groupIds).stream().flatMap(group -> group.getModules().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return Optional.of(Set.copyOf(result));
    }

    private void syncMembers(PermissionGroup group, Collection<UUID> requested) {
        Set<UUID> memberIds = Set.copyOf(requested);
        for (UUID userId : memberIds) requireActiveMember(group.getOrganizationId(), userId);
        List<PermissionGroupMember> existing = groupMembers.findByGroupId(group.getId());
        Set<UUID> existingIds = existing.stream().map(PermissionGroupMember::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        groupMembers.deleteAll(existing.stream().filter(value -> !memberIds.contains(value.getUserId())).toList());
        groupMembers.saveAll(memberIds.stream().filter(userId -> !existingIds.contains(userId))
                .map(userId -> new PermissionGroupMember(group.getOrganizationId(), group.getId(), userId)).toList());
    }

    private GroupView view(PermissionGroup group) {
        List<UUID> memberIds = groupMembers.findByGroupId(group.getId()).stream()
                .map(PermissionGroupMember::getUserId).sorted().toList();
        return new GroupView(group.getId(), group.getOrganizationId(), group.getName(), group.getDescription(),
                group.getModules().stream().sorted().toList(), memberIds, group.getCreatedAt(), group.getUpdatedAt());
    }

    private Map<String, Object> snapshot(PermissionGroup group) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", group.getName());
        value.put("description", group.getDescription());
        value.put("modules", group.getModules().stream().sorted().toList());
        value.put("memberIds", groupMembers.findByGroupId(group.getId()).stream()
                .map(PermissionGroupMember::getUserId).sorted().toList());
        return value;
    }

    private PermissionGroup requireGroup(UUID organizationId, UUID groupId) {
        return groups.findByIdAndOrganizationId(groupId, organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "permission_group_not_found",
                        "Permission group not found"));
    }

    private void requireAdmin(UUID organizationId, UUID actorId, boolean root) {
        if (root) return;
        var membership = requireActiveMember(organizationId, actorId);
        if (membership.getRole() != OrganizationMembership.Role.OWNER
                && membership.getRole() != OrganizationMembership.Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "organization_admin_required",
                    "Organization admin access is required");
        }
    }

    private OrganizationMembership requireActiveMember(UUID organizationId, UUID userId) {
        return memberships.findByOrganizationIdAndUserId(organizationId, userId)
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "organization_member_required",
                        "Permission group users must be active organization members"));
    }

    private Set<String> modules(Collection<String> values) {
        if (values == null) return Set.of();
        Set<String> result = values.stream().map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!AVAILABLE_MODULES.containsAll(result)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_module", "Permission group contains an invalid module");
        }
        return Set.copyOf(result);
    }

    private String name(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_permission_group_name", "Name must be 1-100 characters");
        }
        return result;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_permission_group_description",
                    "Description must not exceed 500 characters");
        }
        return result;
    }

    public record GroupView(UUID id, UUID organizationId, String name, String description, List<String> modules,
                            List<UUID> memberIds, Instant createdAt, Instant updatedAt) {}
}
