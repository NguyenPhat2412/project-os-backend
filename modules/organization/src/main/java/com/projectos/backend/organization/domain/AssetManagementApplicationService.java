package com.projectos.backend.organization.domain;

import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.api.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetManagementApplicationService {
    private final OrganizationRepository organizations;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final OrganizationMembershipRepository memberships;
    private final CompanyAssetRepository assets;
    private final CompanyResourceRepository resources;
    private final AssetHandoverOrderRepository handovers;
    private final AssetHandoverItemRepository handoverItems;
    private final AssetAssignmentRepository assignments;

    AssetManagementApplicationService(OrganizationRepository organizations, EmployeeRepository employees, DepartmentRepository departments,
                                      OrganizationMembershipRepository memberships, CompanyAssetRepository assets, CompanyResourceRepository resources,
                                      AssetHandoverOrderRepository handovers, AssetHandoverItemRepository handoverItems, AssetAssignmentRepository assignments) {
        this.organizations = organizations; this.employees = employees; this.departments = departments; this.memberships = memberships;
        this.assets = assets; this.resources = resources; this.handovers = handovers; this.handoverItems = handoverItems; this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetView> assets(UUID organizationId, int page, int size, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        Page<CompanyAsset> result = assets.findByOrganizationIdAndDeletedFalse(organizationId, pageable(page, size));
        return PageResponse.of(result.getContent().stream().map(this::assetView).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AssetView createAsset(UUID organizationId, AssetInput input, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        String code = required(input.code(), "code");
        if (assets.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(organizationId, code))
            throw new ApiException(HttpStatus.CONFLICT, "asset_code_exists", "Mã tài sản đã tồn tại trong doanh nghiệp.");
        CompanyAsset asset = new CompanyAsset(organizationId, code, required(input.name(), "name"), required(input.category(), "category"), assetStatus(input.status()), actor);
        asset.update(null, null, null, input.serialNumber(), input.model(), input.manufacturer(), date(input.purchaseDate(), "purchaseDate"), input.purchasePrice(), input.currency(), date(input.warrantyUntil(), "warrantyUntil"), input.location(), null, input.supplier(), input.notes(), actor);
        return assetView(assets.save(asset));
    }

    @Transactional
    public AssetView updateAsset(UUID organizationId, UUID assetId, AssetInput input, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        CompanyAsset asset = requireAsset(organizationId, assetId);
        if (input.code() != null && !input.code().equalsIgnoreCase(asset.getCode()) && assets.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(organizationId, input.code().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "asset_code_exists", "Mã tài sản đã tồn tại trong doanh nghiệp.");
        asset.update(input.code(), input.name(), input.category(), input.serialNumber(), input.model(), input.manufacturer(), date(input.purchaseDate(), "purchaseDate"), input.purchasePrice(), input.currency(), date(input.warrantyUntil(), "warrantyUntil"), input.location(), input.status() == null ? null : assetStatus(input.status()), input.supplier(), input.notes(), actor);
        return assetView(assets.save(asset));
    }

    @Transactional
    public void deleteAsset(UUID organizationId, UUID assetId, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        CompanyAsset asset = requireAsset(organizationId, assetId);
        if (assignments.findByAssetIdAndStatus(assetId, AssetAssignment.Status.ACTIVE).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "asset_assignment_active", "Không thể xóa tài sản đang được bàn giao.");
        asset.markDeleted(actor);
        assets.save(asset);
    }

    @Transactional(readOnly = true)
    public PageResponse<ResourceView> resources(UUID organizationId, int page, int size, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        Page<CompanyResource> result = resources.findByOrganizationId(organizationId, pageable(page, size));
        return PageResponse.of(result.getContent().stream().map(ResourceView::from).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ResourceView createResource(UUID organizationId, ResourceInput input, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        String code = required(input.code(), "code");
        if (resources.existsByOrganizationIdAndCodeIgnoreCase(organizationId, code))
            throw new ApiException(HttpStatus.CONFLICT, "resource_code_exists", "Mã tài nguyên đã tồn tại trong doanh nghiệp.");
        int quantity = input.quantity() == null ? 1 : input.quantity();
        if (quantity < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_resource_quantity", "Số lượng tài nguyên phải lớn hơn 0.");
        if (input.ownerDepartmentId() != null) requireDepartment(organizationId, input.ownerDepartmentId());
        CompanyResource resource = new CompanyResource(organizationId, code, required(input.name(), "name"), required(input.category(), "category"), quantity, actor);
        resource.update(null, null, null, null, input.unit(), input.location(), input.ownerDepartmentId(), input.bookable(), resourceStatus(input.status()), input.notes(), actor);
        return ResourceView.from(resources.save(resource));
    }

    @Transactional
    public ResourceView updateResource(UUID organizationId, UUID resourceId, ResourceInput input, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        CompanyResource resource = resources.findByOrganizationIdAndId(organizationId, resourceId).orElseThrow(() -> notFound("resource_not_found", "Không tìm thấy tài nguyên trong doanh nghiệp."));
        if (input.ownerDepartmentId() != null) requireDepartment(organizationId, input.ownerDepartmentId());
        if (input.quantity() != null && input.quantity() < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_resource_quantity", "Số lượng tài nguyên phải lớn hơn 0.");
        if (input.code() != null && !input.code().equalsIgnoreCase(resource.getCode()) && resources.existsByOrganizationIdAndCodeIgnoreCase(organizationId, input.code().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "resource_code_exists", "Mã tài nguyên đã tồn tại trong doanh nghiệp.");
        resource.update(input.code(), input.name(), input.category(), input.quantity(), input.unit(), input.location(), input.ownerDepartmentId(), input.bookable(), input.status() == null ? null : resourceStatus(input.status()), input.notes(), actor);
        return ResourceView.from(resources.save(resource));
    }

    @Transactional
    public void deleteResource(UUID organizationId, UUID resourceId, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        CompanyResource resource = resources.findByOrganizationIdAndId(organizationId, resourceId).orElseThrow(() -> notFound("resource_not_found", "Không tìm thấy tài nguyên trong doanh nghiệp."));
        resource.update(null, null, null, null, null, null, null, null, CompanyResource.Status.INACTIVE, null, actor);
        resources.save(resource);
    }

    @Transactional
    public HandoverView createHandover(UUID organizationId, HandoverInput input, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        Employee employee = requireEmployee(organizationId, input.employeeId());
        if (input.assetIds() == null || input.assetIds().isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "asset_items_required", "Cần chọn ít nhất một tài sản để bàn giao.");
        AssetHandoverOrder order = new AssetHandoverOrder(organizationId, employee.getId(), required(input.purpose(), "purpose"), input.notes(), actor);
        for (UUID assetId : input.assetIds()) {
            CompanyAsset asset = requireAsset(organizationId, assetId);
            if (asset.getStatus() != CompanyAsset.Status.AVAILABLE || assignments.findByAssetIdAndStatus(assetId, AssetAssignment.Status.ACTIVE).isPresent())
                throw new ApiException(HttpStatus.CONFLICT, "asset_not_available", "Tài sản đã được cấp phát hoặc chưa sẵn sàng để bàn giao.");
        }
        order = handovers.save(order);
        for (UUID assetId : input.assetIds()) handoverItems.save(new AssetHandoverItem(order.getId(), assetId, input.conditionOut(), input.notes()));
        return handoverView(order, employee);
    }

    @Transactional
    public HandoverView confirmHandover(UUID organizationId, UUID handoverId, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        AssetHandoverOrder order = requireHandover(organizationId, handoverId);
        if (order.getStatus() != AssetHandoverOrder.Status.PENDING) throw new ApiException(HttpStatus.CONFLICT, "handover_invalid_state", "Phiếu bàn giao không còn ở trạng thái chờ xác nhận.");
        Employee employee = requireEmployee(organizationId, order.getEmployeeId());
        for (AssetHandoverItem item : handoverItems.findByHandoverId(handoverId)) {
            CompanyAsset asset = requireAsset(organizationId, item.getAssetId());
            if (asset.getStatus() != CompanyAsset.Status.AVAILABLE || assignments.findByAssetIdAndStatus(asset.getId(), AssetAssignment.Status.ACTIVE).isPresent())
                throw new ApiException(HttpStatus.CONFLICT, "asset_not_available", "Có tài sản không còn sẵn sàng để bàn giao.");
            item.issue(); handoverItems.save(item);
            asset.update(null, null, null, null, null, null, null, null, null, null, null, CompanyAsset.Status.IN_USE, null, null, actor);
            assets.save(asset);
            assignments.save(new AssetAssignment(organizationId, asset.getId(), employee.getId(), handoverId, actor, order.getPurpose()));
        }
        order.confirm();
        return handoverView(handovers.save(order), employee);
    }

    @Transactional
    public HandoverView returnHandover(UUID organizationId, UUID handoverId, String conditionIn, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        AssetHandoverOrder order = requireHandover(organizationId, handoverId);
        if (order.getStatus() != AssetHandoverOrder.Status.CONFIRMED) throw new ApiException(HttpStatus.CONFLICT, "handover_invalid_state", "Phiếu bàn giao chưa được xác nhận hoặc đã hoàn tất.");
        Employee employee = requireEmployee(organizationId, order.getEmployeeId());
        for (AssetHandoverItem item : handoverItems.findByHandoverId(handoverId)) {
            item.returnItem(conditionIn); handoverItems.save(item);
            assignments.findByAssetIdAndStatus(item.getAssetId(), AssetAssignment.Status.ACTIVE).ifPresent(assignment -> { assignment.returned(actor); assignments.save(assignment); });
            CompanyAsset asset = requireAsset(organizationId, item.getAssetId());
            asset.update(null, null, null, null, null, null, null, null, null, null, null, conditionIn == null || conditionIn.isBlank() ? CompanyAsset.Status.AVAILABLE : CompanyAsset.Status.MAINTENANCE, null, null, actor);
            assets.save(asset);
        }
        order.returned();
        return handoverView(handovers.save(order), employee);
    }

    @Transactional(readOnly = true)
    public List<AssetView> employeeAssets(UUID organizationId, UUID employeeId, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        Employee employee = requireEmployee(organizationId, employeeId);
        return assignments.findByOrganizationIdAndEmployeeIdAndStatus(organizationId, employee.getId(), AssetAssignment.Status.ACTIVE).stream()
                .map(assignment -> assets.findByOrganizationIdAndIdAndDeletedFalse(organizationId, assignment.getAssetId()).map(this::assetView).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public List<HandoverHistoryView> assetHistory(UUID organizationId, UUID assetId, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        requireAsset(organizationId, assetId);
        return handoverItems.findByAssetIdOrderByIssuedAtDesc(assetId).stream().map(item -> {
            AssetHandoverOrder order = handovers.findById(item.getHandoverId()).orElse(null);
            Employee employee = order == null ? null : employees.findById(order.getEmployeeId()).orElse(null);
            return new HandoverHistoryView(item.getId(), order == null ? null : order.getId(), employee == null ? null : employee.getId(), employee == null ? null : employee.getFullName(), employee == null ? null : employee.getCode(), item.getIssuedAt(), item.getReturnedAt(), order == null ? null : order.getPurpose(), order == null ? null : order.getStatus().name().toLowerCase(Locale.ROOT));
        }).toList();
    }

    private AssetView assetView(CompanyAsset asset) {
        var current = assignments.findByAssetIdAndStatus(asset.getId(), AssetAssignment.Status.ACTIVE).orElse(null);
        Employee employee = current == null ? null : employees.findById(current.getEmployeeId()).orElse(null);
        return AssetView.from(asset, employee, current == null ? null : current.getAssignedAt());
    }
    private HandoverView handoverView(AssetHandoverOrder order, Employee employee) { return new HandoverView(order.getId(), order.getOrganizationId(), employee.getId(), employee.getFullName(), employee.getCode(), order.getStatus().name().toLowerCase(Locale.ROOT), order.getPurpose(), order.getNotes(), order.getCreatedAt(), order.getConfirmedAt(), order.getReturnedAt(), handoverItems.findByHandoverId(order.getId()).stream().map(AssetHandoverItem::getAssetId).toList()); }
    private CompanyAsset requireAsset(UUID organizationId, UUID id) { return assets.findByOrganizationIdAndIdAndDeletedFalse(organizationId, id).orElseThrow(() -> notFound("asset_not_found", "Không tìm thấy tài sản trong doanh nghiệp.")); }
    private AssetHandoverOrder requireHandover(UUID organizationId, UUID id) { return handovers.findByOrganizationIdAndId(organizationId, id).orElseThrow(() -> notFound("handover_not_found", "Không tìm thấy phiếu bàn giao.")); }
    private Employee requireEmployee(UUID organizationId, UUID id) { return employees.findById(id).filter(value -> value.getOrganizationId().equals(organizationId) && !value.isDeleted()).orElseThrow(() -> notFound("employee_not_found", "Nhân sự không tồn tại trong doanh nghiệp.")); }
    private Department requireDepartment(UUID organizationId, UUID id) { return departments.findById(id).filter(value -> value.getOrganizationId().equals(organizationId)).orElseThrow(() -> notFound("department_not_found", "Phòng ban không tồn tại trong doanh nghiệp.")); }
    private void requireMember(UUID organizationId, UUID actor, boolean root) { if (root) { requireOrganization(organizationId); return; } requireOrganization(organizationId); memberships.findByOrganizationIdAndUserId(organizationId, actor).filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Bạn không có quyền truy cập doanh nghiệp này.")); }
    private void requireHrOrAdmin(UUID organizationId, UUID actor, boolean root) { requireMember(organizationId, actor, root); if (root) return; var role = memberships.findByOrganizationIdAndUserId(organizationId, actor).orElseThrow().getRole(); if (role != OrganizationMembership.Role.OWNER && role != OrganizationMembership.Role.ADMIN && role != OrganizationMembership.Role.HR) throw new ApiException(HttpStatus.FORBIDDEN, "hr_access_required", "Bạn không có quyền quản lý tài sản của doanh nghiệp."); }
    private void requireOrganization(UUID id) { if (!organizations.existsById(id)) throw notFound("organization_not_found", "Không tìm thấy doanh nghiệp."); }
    private PageRequest pageable(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "Tham số phân trang không hợp lệ."); return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")); }
    private String required(String value, String field) { if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "missing_" + field, "Trường " + field + " là bắt buộc."); return value.trim(); }
    private LocalDate date(String value, String field) { if (value == null || value.isBlank()) return null; try { return LocalDate.parse(value); } catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_" + field, "Ngày " + field + " không hợp lệ."); } }
    private CompanyAsset.Status assetStatus(String value) { try { return value == null || value.isBlank() ? CompanyAsset.Status.AVAILABLE : CompanyAsset.Status.valueOf(value.toUpperCase(Locale.ROOT)); } catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_asset_status", "Trạng thái tài sản không hợp lệ."); } }
    private CompanyResource.Status resourceStatus(String value) { try { return value == null || value.isBlank() ? CompanyResource.Status.ACTIVE : CompanyResource.Status.valueOf(value.toUpperCase(Locale.ROOT)); } catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_resource_status", "Trạng thái tài nguyên không hợp lệ."); } }
    private ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    public record AssetInput(String code, String name, String category, String serialNumber, String model, String manufacturer, String purchaseDate, BigDecimal purchasePrice, String currency, String warrantyUntil, String location, String status, String supplier, String notes) {}
    public record ResourceInput(String code, String name, String category, Integer quantity, String unit, String location, UUID ownerDepartmentId, Boolean bookable, String status, String notes) {}
    public record HandoverInput(UUID employeeId, List<UUID> assetIds, String purpose, String conditionOut, String notes) {}
    public record AssetView(UUID id, UUID organizationId, String code, String name, String category, String serialNumber, String model, String manufacturer, LocalDate purchaseDate, BigDecimal purchasePrice, String currency, LocalDate warrantyUntil, String location, String status, String supplier, String notes, String currentAssignee, String assigneeCode, UUID assigneeId, java.time.Instant assignedAt, Instant createdAt, Instant updatedAt) {
        static AssetView from(CompanyAsset asset, Employee employee, Instant assignedAt) { return new AssetView(asset.getId(), asset.getOrganizationId(), asset.getCode(), asset.getName(), asset.getCategory(), asset.getSerialNumber(), asset.getModel(), asset.getManufacturer(), asset.getPurchaseDate(), asset.getPurchasePrice(), asset.getCurrency(), asset.getWarrantyUntil(), asset.getLocation(), asset.getStatus().name().toLowerCase(Locale.ROOT), asset.getSupplier(), asset.getNotes(), employee == null ? null : employee.getFullName(), employee == null ? null : employee.getCode(), employee == null ? null : employee.getId(), assignedAt, asset.getCreatedAt(), asset.getUpdatedAt()); }
    }
    public record ResourceView(UUID id, UUID organizationId, String code, String name, String category, int quantity, String unit, String location, UUID ownerDepartmentId, boolean bookable, String status, String notes, Instant createdAt, Instant updatedAt) { static ResourceView from(CompanyResource value) { return new ResourceView(value.getId(), value.getOrganizationId(), value.getCode(), value.getName(), value.getCategory(), value.getQuantity(), value.getUnit(), value.getLocation(), value.getOwnerDepartmentId(), value.isBookable(), value.getStatus().name().toLowerCase(Locale.ROOT), value.getNotes(), value.getCreatedAt(), value.getUpdatedAt()); } }
    public record HandoverView(UUID id, UUID organizationId, UUID employeeId, String employeeName, String employeeCode, String status, String purpose, String notes, Instant createdAt, Instant confirmedAt, Instant returnedAt, List<UUID> assetIds) {}
    public record HandoverHistoryView(UUID id, UUID handoverId, UUID employeeId, String employeeName, String employeeCode, Instant issuedAt, Instant returnedAt, String purpose, String status) {}
}
