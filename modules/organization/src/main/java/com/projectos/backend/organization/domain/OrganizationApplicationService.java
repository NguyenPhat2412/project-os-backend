package com.projectos.backend.organization.domain;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.projectos.backend.organization.EnvironmentConfigService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projectos.backend.organization.web.OrganizationController;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.api.PageResponse;
import com.projectos.backend.platform.organization.OrganizationDirectory.AttendancePolicy;

@Service
public class OrganizationApplicationService {
    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final PositionRepository positions;
    private final EmployeeRepository employees;
    private final EmployeeCompensationRepository compensations;
    private final CompanyPolicyRepository policies;
    private final OrganizationSettingsRepository settingsRepository;
    private final OrganizationMembershipRepository memberships;
    private final PermissionGroupService permissionGroups;
    private final OrganizationPermissionService organizationPermissions;
    private final WorkspaceCache workspaceCache;
    private final OrganizationAuditService audit;
    private final ObjectMapper mapper;
    private final EnvironmentConfigService environmentConfig;

    OrganizationApplicationService(OrganizationRepository organizations, DepartmentRepository departments, PositionRepository positions,
                                   EmployeeRepository employees, EmployeeCompensationRepository compensations, CompanyPolicyRepository policies, OrganizationSettingsRepository settingsRepository, OrganizationMembershipRepository memberships,
                                   PermissionGroupService permissionGroups, OrganizationPermissionService organizationPermissions,
                                   WorkspaceCache workspaceCache,
                                   OrganizationAuditService audit, ObjectMapper mapper,
                                   EnvironmentConfigService environmentConfig) {
        this.organizations = organizations;
        this.departments = departments;
        this.positions = positions;
        this.employees = employees;
        this.compensations = compensations;
        this.policies = policies;
        this.settingsRepository = settingsRepository;
        this.memberships = memberships;
        this.permissionGroups = permissionGroups;
        this.organizationPermissions = organizationPermissions;
        this.workspaceCache = workspaceCache;
        this.audit = audit;
        this.mapper = mapper;
        this.environmentConfig = environmentConfig;
    }

    @Transactional(readOnly = true)
    public JsonNode settings(UUID organizationId, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Organization organization = requireOrganization(organizationId);
        return settingsRepository.findById(organizationId).map(OrganizationSettings::getSettings)
                .orElseGet(() -> defaultSettings(organization));
    }

    @Transactional
    public JsonNode updateSettings(UUID organizationId, JsonNode body, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        if (body == null || !body.isObject()) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_settings", "Settings must be an object");
        if (body.get("action") != null) throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "settings_action_not_supported", "This settings action is not available");
        if (body.toString().length() > 200_000) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "settings_too_large", "Settings payload is too large");
        Organization organization = requireOrganization(organizationId);
        if (body.get("env") != null && environmentConfig.isFileConfigured()) {
            environmentConfig.update(environmentValues((ObjectNode) body.get("env")));
        }
        ObjectNode next = (ObjectNode) defaultSettings(organization);
        settingsRepository.findById(organizationId).ifPresent(existing -> merge(next, existing.getSettings()));
        ObjectNode safeBody = (ObjectNode) body.deepCopy();
        removeMaskedEnvironmentValues(safeBody);
        merge(next, safeBody);
        OrganizationSettings settings = settingsRepository.findById(organizationId)
                .orElseGet(() -> new OrganizationSettings(organizationId, next, actor));
        settings.replace(next, actor);
        return settingsRepository.save(settings).getSettings();
    }

    @Transactional(readOnly = true)
    public AttendancePolicy attendancePolicy(UUID organizationId) {
        Organization organization = requireOrganization(organizationId);
        JsonNode root = settingsRepository.findById(organizationId).map(OrganizationSettings::getSettings)
                .orElseGet(() -> defaultSettings(organization));
        JsonNode hr = root.path("hr");
        double latitude = hr.path("officeLatitude").asDouble(0);
        double longitude = hr.path("officeLongitude").asDouble(0);
        int radius = hr.path("gpsCheckInRadiusMeters").asInt(0);
        boolean configured = radius > 0 && latitude != 0 && longitude != 0;
        return new AttendancePolicy(configured, latitude, longitude, radius,
                hr.path("organizationName").asText(organization.getName()));
    }

    private void merge(ObjectNode target, JsonNode source) {
        source.properties().forEach(field -> {
            JsonNode value = field.getValue();
            if (value != null && value.isObject() && target.get(field.getKey()) != null && target.get(field.getKey()).isObject()) {
                merge((ObjectNode) target.get(field.getKey()), value);
            } else if (value != null) {
                target.set(field.getKey(), value.deepCopy());
            }
        });
    }

    private JsonNode defaultSettings(Organization organization) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode general = root.putObject("general");
        general.put("companyName", organization.getName()); general.put("brandName", organization.getName()); general.put("taxCode", ""); general.put("website", ""); general.put("headquartersAddress", ""); general.put("supportPhone", ""); general.put("supportEmail", ""); general.put("timezone", organization.getTimezone()); general.put("currency", "VND"); general.put("dateFormat", "DD/MM/YYYY");
        ObjectNode ai = root.putObject("ai"); ai.put("provider", "OPENAI"); ai.put("modelName", ""); ai.put("temperature", 0.3); ai.put("maxOutputTokens", 2000); ai.put("enableTaskBugAssistant", false); ai.put("enableAttendanceAnomalyDetection", false); ai.put("enableDailyReportSummarizer", false); ai.put("enableContractJDGenerator", false); ai.put("apiKeyMasked", "");
        ObjectNode project = root.putObject("project"); project.put("defaultSprintDurationWeeks", 2); project.put("sprintStartDay", "monday"); project.put("autoCloseSprintOnEndDate", false); ObjectNode sla = project.putObject("bugSlaHours"); sla.put("p0Critical", 4); sla.put("p1High", 24); sla.put("p2Medium", 72); sla.put("p3Low", 168); project.put("budgetWarningThresholdPercent", 80); project.put("enableOverdueNotifications", true); project.put("requireTaskReviewBeforeDone", false);
        ObjectNode hr = root.putObject("hr"); hr.put("organizationId", organization.getId().toString()); hr.put("organizationName", organization.getName()); hr.put("gpsCheckInRadiusMeters", 100); hr.put("officeLatitude", 0); hr.put("officeLongitude", 0); hr.put("workShiftMorningStart", "08:00"); hr.put("workShiftMorningEnd", "12:00"); hr.put("workShiftAfternoonStart", "13:30"); hr.put("workShiftAfternoonEnd", "17:30"); hr.put("allowLateGraceMinutes", 10); hr.put("annualLeaveDaysPerYear", 12); hr.put("seniorityBonusLeaveYears", 0); hr.put("contractExpiringAlertDays", 30); hr.put("maternityNursingMonths", 0); hr.put("autoLockOutOfRadiusCheckIn", false);
        ObjectNode messenger = root.putObject("messenger"); messenger.put("wsUrl", ""); messenger.put("wsPort", 0); messenger.put("storageProvider", "LOCAL_SERVER"); messenger.put("s3BucketName", ""); messenger.put("s3Region", ""); messenger.put("maxAttachmentSizeMb", 25); messenger.put("allowedExtensions", ""); messenger.put("autoCleanTempFilesDays", 0);
        ObjectNode notifications = root.putObject("notifications"); notifications.put("smtpHost", ""); notifications.put("smtpPort", 587); notifications.put("smtpEncryption", "TLS"); notifications.put("smtpSenderAddress", ""); notifications.put("smtpSenderName", ""); notifications.put("smtpUser", ""); notifications.put("smtpPasswordMasked", ""); notifications.put("enableEmailOnLeaveRequest", false); notifications.put("enableEmailOnWorkplaceAccident", false); notifications.put("enableEmailOnContractExpiry", false); notifications.put("enableTelegramWebhook", false); notifications.put("telegramBotTokenMasked", ""); notifications.put("telegramChatId", ""); notifications.put("enableSlackWebhook", false); notifications.put("slackWebhookUrl", "");
        ObjectNode security = root.putObject("security"); security.put("enforce2FAForAdmins", false); security.put("sessionTimeoutMinutes", 480); security.put("maxFailedLoginAttempts", 5); security.put("lockoutDurationMinutes", 15); security.put("ipWhitelistEnabled", false); security.putArray("allowedIpRanges"); security.put("autoDailyBackupEnabled", false); security.put("backupTimeUtc", ""); security.put("backupRetentionDays", 30);
        ObjectNode env = root.putObject("env"); env.put("projectOsApiPublicUrl", ""); env.put("projectOsApiInternalUrl", ""); env.put("gatewayPort", 0); env.put("corsAllowedOrigins", ""); env.put("postgresHost", ""); env.put("postgresPort", 0); env.put("postgresDb", ""); env.put("postgresUser", ""); env.put("postgresPasswordMasked", ""); env.put("redisHost", ""); env.put("redisPort", 0); env.put("nextPublicWsUrl", ""); env.put("wsPort", 0); env.put("s3Bucket", ""); env.put("s3Region", ""); env.put("s3AccessKey", ""); env.put("s3SecretKeyMasked", ""); env.put("s3Endpoint", ""); env.put("minioRootUser", ""); env.put("minioRootPasswordMasked", ""); env.put("jwtSecretMasked", ""); env.put("internalServiceTokenMasked", ""); env.put("bootstrapAdminEmail", ""); env.put("bootstrapAdminPasswordMasked", ""); env.put("bootstrapAdminName", ""); env.put("anthropicApiKeyMasked", ""); env.put("geminiApiKeyMasked", ""); env.put("googleClientId", ""); env.put("googleClientSecretMasked", ""); env.put("googleOauthRedirectUri", "");
        applyEnvironmentSnapshot(env);
        env.put("environmentFileConfigured", environmentConfig.isFileConfigured());
        root.put("updatedAt", ""); root.put("updatedBy", "");
        return root;
    }

    private void applyEnvironmentSnapshot(ObjectNode env) {
        Map<String, String> values = environmentConfig.snapshot();
        env.put("projectOsApiPublicUrl", value(values, "PROJECT_OS_API_PUBLIC_URL"));
        env.put("projectOsApiInternalUrl", value(values, "PROJECT_OS_API_INTERNAL_URL"));
        env.put("gatewayPort", integer(values, "GATEWAY_PORT"));
        env.put("corsAllowedOrigins", value(values, "CORS_ALLOWED_ORIGINS"));
        env.put("postgresHost", value(values, "POSTGRES_HOST"));
        env.put("postgresPort", integer(values, "POSTGRES_PORT"));
        env.put("postgresDb", value(values, "POSTGRES_DB"));
        env.put("postgresUser", value(values, "POSTGRES_USER"));
        env.put("postgresPasswordMasked", value(values, "POSTGRES_PASSWORD"));
        env.put("redisHost", value(values, "REDIS_HOST"));
        env.put("redisPort", integer(values, "REDIS_PORT"));
        env.put("nextPublicWsUrl", value(values, "NEXT_PUBLIC_WS_URL"));
        env.put("wsPort", integer(values, "WS_PORT"));
        env.put("s3Bucket", value(values, "S3_BUCKET"));
        env.put("s3Region", value(values, "S3_REGION"));
        env.put("s3AccessKey", value(values, "S3_ACCESS_KEY"));
        env.put("s3SecretKeyMasked", value(values, "S3_SECRET_KEY"));
        env.put("s3Endpoint", value(values, "S3_ENDPOINT"));
        env.put("minioRootUser", value(values, "MINIO_ROOT_USER"));
        env.put("minioRootPasswordMasked", value(values, "MINIO_ROOT_PASSWORD"));
        env.put("jwtSecretMasked", value(values, "JWT_SECRET"));
        env.put("internalServiceTokenMasked", value(values, "INTERNAL_SERVICE_TOKEN"));
        env.put("bootstrapAdminEmail", value(values, "BOOTSTRAP_ADMIN_EMAIL"));
        env.put("bootstrapAdminPasswordMasked", value(values, "BOOTSTRAP_ADMIN_PASSWORD"));
        env.put("bootstrapAdminName", value(values, "BOOTSTRAP_ADMIN_NAME"));
        env.put("anthropicApiKeyMasked", value(values, "ANTHROPIC_API_KEY"));
        env.put("geminiApiKeyMasked", value(values, "GEMINI_API_KEY"));
        env.put("googleClientId", value(values, "GOOGLE_CLIENT_ID"));
        env.put("googleClientSecretMasked", value(values, "GOOGLE_CLIENT_SECRET"));
        env.put("googleOauthRedirectUri", value(values, "GOOGLE_OAUTH_REDIRECT_URI"));
    }

    private Map<String, String> environmentValues(ObjectNode env) {
        Map<String, String> values = new HashMap<>();
        put(values, "PROJECT_OS_API_PUBLIC_URL", env, "projectOsApiPublicUrl");
        put(values, "PROJECT_OS_API_INTERNAL_URL", env, "projectOsApiInternalUrl");
        put(values, "GATEWAY_PORT", env, "gatewayPort");
        put(values, "CORS_ALLOWED_ORIGINS", env, "corsAllowedOrigins");
        put(values, "POSTGRES_HOST", env, "postgresHost");
        put(values, "POSTGRES_PORT", env, "postgresPort");
        put(values, "POSTGRES_DB", env, "postgresDb");
        put(values, "POSTGRES_USER", env, "postgresUser");
        put(values, "POSTGRES_PASSWORD", env, "postgresPasswordMasked");
        put(values, "REDIS_HOST", env, "redisHost");
        put(values, "REDIS_PORT", env, "redisPort");
        put(values, "NEXT_PUBLIC_WS_URL", env, "nextPublicWsUrl");
        put(values, "WS_PORT", env, "wsPort");
        put(values, "S3_BUCKET", env, "s3Bucket");
        put(values, "S3_REGION", env, "s3Region");
        put(values, "S3_ACCESS_KEY", env, "s3AccessKey");
        put(values, "S3_SECRET_KEY", env, "s3SecretKeyMasked");
        put(values, "S3_ENDPOINT", env, "s3Endpoint");
        put(values, "MINIO_ROOT_USER", env, "minioRootUser");
        put(values, "MINIO_ROOT_PASSWORD", env, "minioRootPasswordMasked");
        put(values, "JWT_SECRET", env, "jwtSecretMasked");
        put(values, "INTERNAL_SERVICE_TOKEN", env, "internalServiceTokenMasked");
        put(values, "BOOTSTRAP_ADMIN_EMAIL", env, "bootstrapAdminEmail");
        put(values, "BOOTSTRAP_ADMIN_PASSWORD", env, "bootstrapAdminPasswordMasked");
        put(values, "BOOTSTRAP_ADMIN_NAME", env, "bootstrapAdminName");
        put(values, "ANTHROPIC_API_KEY", env, "anthropicApiKeyMasked");
        put(values, "GEMINI_API_KEY", env, "geminiApiKeyMasked");
        put(values, "GOOGLE_CLIENT_ID", env, "googleClientId");
        put(values, "GOOGLE_CLIENT_SECRET", env, "googleClientSecretMasked");
        put(values, "GOOGLE_OAUTH_REDIRECT_URI", env, "googleOauthRedirectUri");
        return values;
    }

    private void removeMaskedEnvironmentValues(ObjectNode body) {
        JsonNode env = body.get("env");
        if (!(env instanceof ObjectNode environment)) return;
        List<String> maskedFields = new java.util.ArrayList<>();
        environment.properties().forEach(entry -> {
            if (entry.getKey().endsWith("Masked") && EnvironmentConfigService.MASKED_VALUE.equals(entry.getValue().asText())) {
                maskedFields.add(entry.getKey());
            }
        });
        maskedFields.forEach(environment::remove);
    }

    private void put(Map<String, String> target, String key, ObjectNode source, String field) {
        if (source.has(field) && !source.get(field).isNull()) target.put(key, source.get(field).asText());
    }

    private String value(Map<String, String> values, String key) { return values.getOrDefault(key, ""); }
    private int integer(Map<String, String> values, String key) {
        try { return Integer.parseInt(value(values, key)); } catch (NumberFormatException ignored) { return 0; }
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.OrganizationView> organizations(int page, int size, UUID actor, boolean root) {
        PageRequest pageable = page(page, size);
        var result = (root ? organizations.findAll(pageable) : organizations.findAccessible(actor, pageable))
                .map(OrganizationController.OrganizationView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OrganizationController.OrganizationView create(OrganizationController.OrganizationRequest request, UUID actor, boolean root) {
        if (!root) throw new ApiException(HttpStatus.FORBIDDEN, "root_admin_required", "Root admin access is required");
        String slug = slug(request.slug(), request.name());
        if (organizations.findBySlug(slug).isPresent()) throw conflict("organization_slug_exists", "Organization slug already exists");
        Organization organization = new Organization(request.name().trim(), slug, timezone(request.timezone()), actor);
        organization.update(clean(request.name()), slug, timezone(request.timezone()), Organization.Status.ACTIVE,
                clean(request.code()), clean(request.nameVi()), clean(request.nameEn()), clean(request.shortName()), clean(request.taxCode()),
                clean(request.legalRepresentative()), clean(request.representativeTitle()), clean(request.headquartersAddress()),
                clean(request.hotline()), request.email() == null ? null : email(request.email()), clean(request.website()),
                profileDetails(null, request.foundedDate(), request.charterCapital(), request.businessType(), request.industry(),
                        request.loginSubdomain(), request.domainTail(), request.adminEmail()));
        organizations.save(organization);
        memberships.save(new OrganizationMembership(organization.getId(), actor, OrganizationMembership.Role.OWNER));
        return OrganizationController.OrganizationView.from(organization);
    }

    @Transactional(readOnly = true)
    public OrganizationController.OrganizationView organization(UUID id, UUID actor, boolean root) {
        requireMember(id, actor, root);
        return OrganizationController.OrganizationView.from(requireOrganization(id));
    }

    @Transactional
    public OrganizationController.OrganizationView updateOrganization(UUID id, OrganizationController.OrganizationPatch request, UUID actor, boolean root) {
        requireAdmin(id, actor, root);
        Organization organization = requireOrganization(id);
        String nextSlug = request.slug() == null ? null : slug(request.slug(), organization.getName());
        if (nextSlug != null && !nextSlug.equals(organization.getSlug()) && organizations.findBySlug(nextSlug).isPresent()) {
            throw conflict("organization_slug_exists", "Organization slug already exists");
        }
        organization.update(clean(request.name()), nextSlug, request.timezone() == null ? null : timezone(request.timezone()), status(request.status(), Organization.Status.class),
                clean(request.code()), clean(request.nameVi()), clean(request.nameEn()), clean(request.shortName()), clean(request.taxCode()),
                clean(request.legalRepresentative()), clean(request.representativeTitle()), clean(request.headquartersAddress()),
                clean(request.hotline()), request.email() == null ? null : email(request.email()), clean(request.website()),
                profileDetails(organization.getDetails(), request.foundedDate(), request.charterCapital(), request.businessType(), request.industry(),
                        request.loginSubdomain(), request.domainTail(), request.adminEmail()));
        workspaceCache.invalidateOrganization(id);
        return OrganizationController.OrganizationView.from(organization);
    }

    @Transactional(readOnly = true)
    public OrganizationController.CompanyPolicyView companyPolicy(UUID organizationId, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        requireOrganization(organizationId);
        return policies.findById(organizationId).map(OrganizationController.CompanyPolicyView::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "company_policy_not_configured", "Company policy is not configured"));
    }

    @Transactional
    public OrganizationController.CompanyPolicyView updateCompanyPolicy(UUID organizationId,
                                                                          OrganizationController.CompanyPolicyRequest request,
                                                                          UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        requireOrganization(organizationId);
        validateCompanyPolicy(request);
        List<String> rules = request.rules().stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
        CompanyPolicy policy = policies.findById(organizationId).orElseGet(() -> new CompanyPolicy(organizationId,
                request.morningStart(), request.morningEnd(), request.afternoonStart(), request.afternoonEnd(), rules, actor));
        policy.update(request.morningStart(), request.morningEnd(), request.afternoonStart(), request.afternoonEnd(), rules, actor);
        CompanyPolicy saved = policies.save(policy);
        audit.record(organizationId, actor, "company_policy_updated", "company_policy", organizationId,
                null, Map.of("ruleCount", rules.size()), null);
        return OrganizationController.CompanyPolicyView.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.DepartmentView> departments(UUID organizationId, int page, int size, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        var result = departments.findByOrganizationId(organizationId, page(page, size)).map(OrganizationController.DepartmentView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OrganizationController.DepartmentView createDepartment(UUID organizationId, OrganizationController.DepartmentRequest request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        validateParent(organizationId, request.parentId(), null);
        return OrganizationController.DepartmentView.from(departments.save(new Department(organizationId, request.parentId(), request.name().trim())));
    }

    @Transactional
    public OrganizationController.DepartmentView updateDepartment(UUID organizationId, UUID departmentId, OrganizationController.DepartmentPatch request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Department department = requireDepartment(organizationId, departmentId);
        if (request.parentId() != null) validateParent(organizationId, request.parentId(), departmentId);
        department.update(request.parentId(), clean(request.name()));
        return OrganizationController.DepartmentView.from(department);
    }

    @Transactional
    public void deleteDepartment(UUID organizationId, UUID departmentId, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Department department = requireDepartment(organizationId, departmentId);
        if (employees.countByOrganizationIdAndDepartmentIdAndDeletedFalse(organizationId, departmentId) > 0) {
            throw conflict("department_not_empty", "Move employees before deleting this department");
        }
        departments.delete(department);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.PositionView> positions(UUID organizationId, int page, int size, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        var result = positions.findByOrganizationId(organizationId, page(page, size)).map(OrganizationController.PositionView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OrganizationController.PositionView createPosition(UUID organizationId, OrganizationController.PositionRequest request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        validateDepartment(organizationId, request.departmentId());
        String code = request.code().trim();
        if (positions.existsByOrganizationIdAndCodeIgnoreCase(organizationId, code)) throw conflict("position_code_exists", "Position code already exists");
        return OrganizationController.PositionView.from(positions.save(new Position(organizationId, request.departmentId(), code,
                request.title().trim(), clean(request.jobLevel()), request.standardSalary(), clean(request.description()), actor)));
    }

    @Transactional
    public OrganizationController.PositionView updatePosition(UUID organizationId, UUID positionId, OrganizationController.PositionPatch request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Position position = positions.findByOrganizationIdAndId(organizationId, positionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "position_not_found", "Position not found"));
        if (request.departmentId() != null) validateDepartment(organizationId, request.departmentId());
        String code = clean(request.code());
        if (code != null && !code.equalsIgnoreCase(position.getCode()) && positions.existsByOrganizationIdAndCodeIgnoreCase(organizationId, code)) {
            throw conflict("position_code_exists", "Position code already exists");
        }
        position.update(request.departmentId(), code, clean(request.title()), clean(request.jobLevel()), request.standardSalary(), clean(request.description()),
                status(request.status(), Position.Status.class), actor);
        return OrganizationController.PositionView.from(position);
    }

    @Transactional
    public void deletePosition(UUID organizationId, UUID positionId, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Position position = positions.findByOrganizationIdAndId(organizationId, positionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "position_not_found", "Position not found"));
        positions.delete(position);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.EmployeeView> employees(UUID organizationId, int page, int size, UUID actor, boolean root) {
        OrganizationMembership.Role role = memberRole(organizationId, actor, root);
        PageRequest pageable = page(page, size);
        var result = switch (role) {
            case OWNER, ADMIN, HR -> employees.findByOrganizationIdAndDeletedFalse(organizationId, pageable);
            case DEPARTMENT_MANAGER -> employees.findByOrganizationIdAndUserIdAndDeletedFalse(organizationId, actor)
                    .map(manager -> employees.findByOrganizationIdAndSupervisorIdAndDeletedFalse(organizationId, manager.getId(), pageable))
                    .orElseGet(() -> new PageImpl<>(List.of(), pageable, 0));
            case EMPLOYEE, MEMBER -> employees.findByOrganizationIdAndUserIdAndDeletedFalse(organizationId, actor)
                    .map(employee -> new PageImpl<>(List.of(employee), pageable, 1))
                    .orElseGet(() -> new PageImpl<>(List.of(), pageable, 0));
        };
        var views = result.map(OrganizationController.EmployeeView::from);
        return PageResponse.of(views.getContent(), views.getNumber(), views.getSize(), views.getTotalElements(), views.getTotalPages());
    }

    @Transactional
    public OrganizationController.EmployeeView createEmployee(UUID organizationId, OrganizationController.EmployeeRequest request, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        validateDepartment(organizationId, request.departmentId());
        validateSupervisor(organizationId, request.supervisorId(), null);
        String code = clean(request.code());
        validateEmployeeCode(organizationId, code, null);
        Employee employee = new Employee(organizationId, request.departmentId(), request.supervisorId(), code, request.fullName().trim(), email(request.email()), clean(request.phone()), clean(request.title()), clean(request.notes()));
        return OrganizationController.EmployeeView.from(employees.save(employee));
    }

    @Transactional
    public OrganizationController.EmployeeView updateEmployee(UUID organizationId, UUID employeeId, OrganizationController.EmployeePatch request, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        Employee employee = requireEmployee(organizationId, employeeId);
        Map<String, Object> before = employeeSnapshot(employee);
        if (request.departmentId() != null) validateDepartment(organizationId, request.departmentId());
        if (request.supervisorId() != null) validateSupervisor(organizationId, request.supervisorId(), employeeId);
        String code = clean(request.code());
        validateEmployeeCode(organizationId, code, employeeId);
        employee.update(request.departmentId(), request.supervisorId(), code, clean(request.fullName()), request.email() == null ? null : email(request.email()), clean(request.phone()), clean(request.title()), clean(request.notes()), status(request.status(), Employee.Status.class));
        if (request.departmentId() != null || request.supervisorId() != null) {
            audit.record(organizationId, actor, "employee_structure_updated", "employee", employeeId,
                    before, employeeSnapshot(employee), null);
        }
        workspaceCache.invalidateSubject(employee.getUserId());
        return OrganizationController.EmployeeView.from(employee);
    }

    @Transactional
    public OrganizationController.EmployeeView linkUser(UUID organizationId, UUID employeeId, UUID userId, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        employees.findByOrganizationIdAndUserIdAndDeletedFalse(organizationId, userId).filter(found -> !found.getId().equals(employeeId)).ifPresent(found -> {
            throw conflict("employee_user_already_linked", "User is already linked to another employee");
        });
        Employee employee = requireEmployee(organizationId, employeeId);
        employee.linkUser(userId);
        memberships.findByOrganizationIdAndUserId(organizationId, userId).orElseGet(() -> {
            OrganizationMembership created = memberships.save(new OrganizationMembership(organizationId, userId,
                    OrganizationMembership.Role.MEMBER));
            audit.record(organizationId, actor, "membership_created", "organization_membership", created.getId(),
                    null, membershipSnapshot(created), "employee_linked");
            return created;
        });
        workspaceCache.invalidateSubject(userId);
        return OrganizationController.EmployeeView.from(employee);
    }

    @Transactional
    public void deleteEmployee(UUID organizationId, UUID employeeId, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        Employee employee = requireEmployee(organizationId, employeeId);
        audit.record(organizationId, actor, "employee_deleted", "employee", employeeId,
                employeeSnapshot(employee), null, null);
        employees.delete(employee);
        workspaceCache.invalidateSubject(employee.getUserId());
    }

    @Transactional(readOnly = true)
    public OrganizationController.CompensationView compensation(UUID organizationId, UUID employeeId, UUID actor, boolean root) {
        Employee employee = requireEmployee(organizationId, employeeId);
        if (!root) {
            OrganizationMembership.Role role = memberRole(organizationId, actor, false);
            boolean canRead = role == OrganizationMembership.Role.OWNER || role == OrganizationMembership.Role.ADMIN
                    || actor.equals(employee.getUserId());
            if (!canRead) throw new ApiException(HttpStatus.FORBIDDEN, "employee_compensation_access_denied",
                    "You can only view your own compensation");
        }
        return compensations.findByOrganizationIdAndEmployeeId(organizationId, employeeId)
                .map(OrganizationController.CompensationView::from).orElse(null);
    }

    @Transactional
    public OrganizationController.CompensationView updateCompensation(UUID organizationId, UUID employeeId,
                                                                       OrganizationController.CompensationRequest request,
                                                                       UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        Employee employee = requireEmployee(organizationId, employeeId);
        BigDecimal amount = request.monthlyAmount();
        EmployeeCompensation compensation = compensations.findByOrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElseGet(() -> new EmployeeCompensation(organizationId, employeeId, amount, actor));
        compensation.update(amount, actor);
        EmployeeCompensation saved = compensations.save(compensation);
        audit.record(organizationId, actor, "employee_compensation_updated", "employee_compensation", employeeId,
                Map.of("configured", true), Map.of("configured", true), null);
        return OrganizationController.CompensationView.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.MembershipView> memberships(UUID organizationId, int page, int size, UUID actor, boolean root) {
        requireHrOrAdmin(organizationId, actor, root);
        var result = memberships.findByOrganizationId(organizationId, page(page, size)).map(OrganizationController.MembershipView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OrganizationController.MembershipView upsertMembership(UUID organizationId, OrganizationController.MembershipRequest request, UUID actor, boolean root) {
        requireAdmin(organizationId, actor, root);
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, request.userId())
                .orElse(null);
        Map<String, Object> before = membership == null ? null : membershipSnapshot(membership);
        if (membership == null) membership = new OrganizationMembership(organizationId, request.userId(), role(request.role()));
        membership.update(role(request.role()), membershipStatus(request.status()));
        OrganizationMembership saved = memberships.save(membership);
        synchronizeEmployee(organizationId, request, actor);
        audit.record(organizationId, actor, before == null ? "membership_created" : "membership_updated",
                "organization_membership", saved.getId(), before, membershipSnapshot(saved), null);
        workspaceCache.invalidateSubject(request.userId());
        return OrganizationController.MembershipView.from(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationController.EmployeeView employeeByUser(UUID organizationId, UUID userId) {
        return OrganizationController.EmployeeView.from(employees.findByOrganizationIdAndUserIdAndDeletedFalse(organizationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "employee_not_found", "Employee not found")));
    }

    @Transactional(readOnly = true)
    public OrganizationController.EmployeeView employee(UUID organizationId, UUID employeeId) {
        return OrganizationController.EmployeeView.from(requireEmployee(organizationId, employeeId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationController.EmployeeView> directReports(UUID organizationId, UUID supervisorId,
                                                                            int page, int size) {
        var result = employees.findByOrganizationIdAndSupervisorIdAndDeletedFalse(organizationId, supervisorId, page(page, size))
                .map(OrganizationController.EmployeeView::from);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Workspace workspace(UUID requestedOrganizationId, UUID actor, boolean root) {
        return workspace(requestedOrganizationId, null, actor, root);
    }

    @Transactional(readOnly = true)
    public Workspace workspace(UUID requestedOrganizationId, String requestedOrganizationSlug, UUID actor, boolean root) {
        Organization organization = requestedOrganizationId != null
                ? requireOrganization(requestedOrganizationId)
                : requestedOrganizationSlug != null && !requestedOrganizationSlug.isBlank()
                    ? organizations.findBySlug(requestedOrganizationSlug.trim().toLowerCase(Locale.ROOT))
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "organization_not_found", "Organization not found"))
                    : firstOrganization(actor, root);
        Optional<Workspace> cached = workspaceCache.get(organization.getId(), actor);
        if (cached.isPresent()) return cached.get();
        OrganizationMembership.Role membershipRole = memberRole(organization.getId(), actor, root);
        Employee employee = employees.findByOrganizationIdAndUserIdAndDeletedFalse(organization.getId(), actor).orElse(null);
        String systemRole = root || membershipRole == OrganizationMembership.Role.OWNER
                || membershipRole == OrganizationMembership.Role.ADMIN ? "PLATFORM_ADMIN"
                : membershipRole == OrganizationMembership.Role.DEPARTMENT_MANAGER ? "DEPARTMENT_MANAGER"
                : membershipRole == OrganizationMembership.Role.HR ? "HR" : "EMPLOYEE";
        String permissionRole = employee != null && ((employee.getTitle() != null && employee.getTitle().toLowerCase(Locale.ROOT).contains("intern"))
                || (employee.getTitle() != null && employee.getTitle().toLowerCase(Locale.ROOT).contains("thực tập")))
                ? "INTERN" : systemRoleToPermissionRole(systemRole);
        Set<String> modules = workspaceModules(organization.getId(), actor, systemRole, permissionRole, root);
        String departmentName = employee == null || employee.getDepartmentId() == null ? null
                : departments.findById(employee.getDepartmentId()).map(Department::getName).orElse(null);
        Map<String, String> scopes = switch (systemRole) {
            case "PLATFORM_ADMIN", "HR" -> Map.of("employees", "ORGANIZATION", "attendance", "ORGANIZATION",
                    "tasks", "ASSIGNED_PROJECT");
            case "DEPARTMENT_MANAGER" -> Map.of("employees", "DEPARTMENT", "attendance", "DEPARTMENT",
                    "tasks", "ASSIGNED_PROJECT");
            default -> Map.of("employees", "SELF", "attendance", "SELF", "tasks", "SELF");
        };
        Workspace workspace = new Workspace(OrganizationController.OrganizationView.from(organization),
                employee == null ? null : OrganizationController.EmployeeView.from(employee), systemRole,
                departmentName, permissionGroups.assignedGroupNames(organization.getId(), actor),
                modules.stream().sorted().toList(), scopes);
        workspaceCache.put(organization.getId(), actor, workspace);
        return workspace;
    }

    @Transactional(readOnly = true)
    public InternalAccess internalAccess(UUID organizationId, UUID userId) {
        Organization organization = requireOrganization(organizationId);
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, userId)
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied"));
        return new InternalAccess(organization.getTimezone(), membership.getRole().name());
    }

    @Transactional(readOnly = true)
    public String internalTimezone(UUID organizationId) { return requireOrganization(organizationId).getTimezone(); }

    public record InternalAccess(String timezone, String role) {}
    public record Workspace(OrganizationController.OrganizationView organization,
                            OrganizationController.EmployeeView employee, String systemRole,
                            String departmentName, List<String> permissionGroups,
                            List<String> modules, Map<String, String> scopes) {}

    private void requireMember(UUID organizationId, UUID actor, boolean root) {
        if (root) return;
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, actor)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied"));
        if (membership.getStatus() != OrganizationMembership.Status.ACTIVE) throw new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied");
    }
    private OrganizationMembership.Role memberRole(UUID organizationId, UUID actor, boolean root) {
        if (root) return OrganizationMembership.Role.OWNER;
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, actor)
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied"));
        return membership.getRole();
    }
    private Organization firstOrganization(UUID actor, boolean root) {
        PageRequest first = PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "createdAt"));
        var result = root ? organizations.findAll(first) : organizations.findAccessible(actor, first);
        if (result.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "organization_not_found", "No accessible organization found");
        return result.getContent().getFirst();
    }
    private void requireAdmin(UUID organizationId, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        if (root) return;
        OrganizationMembership.Role role = memberships.findByOrganizationIdAndUserId(organizationId, actor).orElseThrow().getRole();
        if (role != OrganizationMembership.Role.OWNER && role != OrganizationMembership.Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "organization_admin_required", "Organization admin access is required");
    }
    private void requireHrOrAdmin(UUID organizationId, UUID actor, boolean root) {
        requireMember(organizationId, actor, root);
        if (root) return;
        OrganizationMembership.Role role = memberships.findByOrganizationIdAndUserId(organizationId, actor)
                .orElseThrow().getRole();
        if (role != OrganizationMembership.Role.OWNER && role != OrganizationMembership.Role.ADMIN
                && role != OrganizationMembership.Role.HR) {
            throw new ApiException(HttpStatus.FORBIDDEN, "hr_access_required",
                    "HR or organization admin access is required");
        }
    }
    private Organization requireOrganization(UUID id) { return organizations.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "organization_not_found", "Organization not found")); }
    private Department requireDepartment(UUID organizationId, UUID id) { return departments.findById(id).filter(value -> value.getOrganizationId().equals(organizationId)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "department_not_found", "Department not found")); }
    private Employee requireEmployee(UUID organizationId, UUID id) {
        return employees.findById(id)
                .filter(value -> value.getOrganizationId().equals(organizationId) && !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "employee_not_found", "Employee not found"));
    }
    private void validateDepartment(UUID organizationId, UUID departmentId) { if (departmentId != null) requireDepartment(organizationId, departmentId); }
    private void validateSupervisor(UUID organizationId, UUID supervisorId, UUID employeeId) {
        if (supervisorId == null) return;
        UUID cursor = supervisorId;
        while (cursor != null) {
            if (cursor.equals(employeeId)) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_supervisor", "Supervisor hierarchy cannot contain a cycle");
            cursor = requireEmployee(organizationId, cursor).getSupervisorId();
        }
    }
    private void validateParent(UUID organizationId, UUID parentId, UUID departmentId) {
        if (parentId == null) return;
        UUID cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(departmentId)) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_department_parent", "Department hierarchy cannot contain a cycle");
            cursor = requireDepartment(organizationId, cursor).getParentId();
        }
    }
    private PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "page must be >= 0 and size must be between 1 and 100");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    }
    private String slug(String input, String fallback) {
        String value = input == null || input.isBlank() ? fallback : input;
        value = value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (value.isBlank() || value.length() > 80) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_slug", "Invalid organization slug");
        return value;
    }
    private String timezone(String value) { try { return ZoneId.of(value == null || value.isBlank() ? "Asia/Ho_Chi_Minh" : value).getId(); } catch (ZoneRulesException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_timezone", "Invalid timezone"); } }
    private String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private JsonNode profileDetails(JsonNode existing, String foundedDate, String charterCapital, String businessType,
                                    String industry, String loginSubdomain, String domainTail, String adminEmail) {
        ObjectNode details = existing != null && existing.isObject()
                ? (ObjectNode) existing.deepCopy()
                : mapper.createObjectNode();
        putIfPresent(details, "foundedDate", foundedDate);
        putIfPresent(details, "charterCapital", charterCapital);
        putIfPresent(details, "businessType", businessType);
        putIfPresent(details, "industry", industry);
        putIfPresent(details, "loginSubdomain", loginSubdomain);
        putIfPresent(details, "domainTail", domainTail);
        putIfPresent(details, "adminEmail", adminEmail);
        return details;
    }

    private void putIfPresent(ObjectNode target, String field, String value) {
        String cleaned = clean(value);
        if (cleaned != null) target.put(field, cleaned);
    }
    private void synchronizeEmployee(UUID organizationId, OrganizationController.MembershipRequest request, UUID actor) {
        boolean hasName = request.fullName() != null;
        boolean hasEmail = request.email() != null;
        if (!hasName && !hasEmail) return;
        if (!hasName || !hasEmail || clean(request.fullName()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_member_profile", "Full name and email must be provided together");
        }
        String fullName = clean(request.fullName());
        String email = email(request.email());
        Employee byUser = employees.findByOrganizationIdAndUserIdAndDeletedFalse(organizationId, request.userId()).orElse(null);
        Employee byEmail = employees.findByOrganizationIdAndEmailIgnoreCaseAndDeletedFalse(organizationId, email).orElse(null);
        if (byUser != null && byEmail != null && !byUser.getId().equals(byEmail.getId())) {
            throw conflict("employee_email_already_linked", "Email is already linked to another employee");
        }
        Employee employee = byUser != null ? byUser : byEmail;
        if (employee != null && employee.getUserId() != null && !request.userId().equals(employee.getUserId())) {
            throw conflict("employee_email_already_linked", "Email is already linked to another employee");
        }
        Map<String, Object> before = employee == null ? null : employeeSnapshot(employee);
        if (employee == null) employee = new Employee(organizationId, null, null, null, fullName, email, null, null, null);
        else employee.update(null, null, null, fullName, email, null, null, null, null);
        employee.linkUser(request.userId());
        Employee saved = employees.save(employee);
        audit.record(organizationId, actor, before == null ? "employee_created" : "employee_synchronized",
                "employee", saved.getId(), before, employeeSnapshot(saved), "membership_synced");
    }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private OrganizationMembership.Role role(String value) { try { return OrganizationMembership.Role.valueOf(value == null ? "MEMBER" : value.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_role", "Invalid organization role"); } }
    private OrganizationMembership.Status membershipStatus(String value) { try { return value == null ? OrganizationMembership.Status.ACTIVE : OrganizationMembership.Status.valueOf(value.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_membership_status", "Invalid membership status"); } }
    private <T extends Enum<T>> T status(String value, Class<T> type) { try { return value == null ? null : Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_status", "Invalid status"); } }
    private void validateCompanyPolicy(OrganizationController.CompanyPolicyRequest request) {
        if (!request.morningStart().isBefore(request.morningEnd())
                || !request.morningEnd().isBefore(request.afternoonStart())
                || !request.afternoonStart().isBefore(request.afternoonEnd())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_company_schedule", "Working periods must be in chronological order");
        }
    }
    private Set<String> defaultModules(String systemRole) {
        return switch (systemRole) {
            case "PLATFORM_ADMIN" -> Set.of("dashboard", "projects", "tasks", "daily-reports", "attendance", "company-rules",
                    "organization", "employees", "project-management", "operations", "knowledge", "activity",
                    "reports", "admin");
            case "HR" -> Set.of("dashboard", "attendance", "company-rules", "organization", "employees");
            case "DEPARTMENT_MANAGER" -> Set.of("dashboard", "projects", "tasks", "daily-reports", "attendance", "company-rules",
                    "employees", "project-management", "reports");
            default -> Set.of("dashboard", "tasks", "daily-reports", "attendance", "company-rules", "profile");
        };
    }

    private String systemRoleToPermissionRole(String systemRole) {
        return switch (systemRole) {
            case "PLATFORM_ADMIN" -> "SUPER_ADMIN";
            case "HR" -> "HR_MANAGER";
            case "DEPARTMENT_MANAGER" -> "DEPT_LEAD";
            default -> "EMPLOYEE";
        };
    }

    private Set<String> workspaceModules(UUID organizationId, UUID actor, String systemRole,
                                         String permissionRole, boolean root) {
        Set<String> configured = organizationPermissions.modulesForRole(organizationId, permissionRole);
        if (!configured.isEmpty()) return configured;
        if (root) return defaultModules(systemRole);
        Optional<Set<String>> assigned = permissionGroups.assignedModules(organizationId, actor);
        if (assigned.isPresent()) return assigned.get();
        return permissionGroups.hasGroups(organizationId) ? baselineModules(systemRole) : defaultModules(systemRole);
    }

    private Set<String> baselineModules(String systemRole) {
        return "PLATFORM_ADMIN".equals(systemRole)
                ? Set.of("dashboard", "organization", "admin", "profile")
                : Set.of("dashboard", "profile");
    }

    private Map<String, Object> employeeSnapshot(Employee employee) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", employee.getCode());
        value.put("departmentId", employee.getDepartmentId());
        value.put("supervisorId", employee.getSupervisorId());
        value.put("userId", employee.getUserId());
        value.put("status", employee.getStatus().name());
        return value;
    }

    private void validateEmployeeCode(UUID organizationId, String code, UUID employeeId) {
        if (code == null) return;
        employees.findByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(organizationId, code)
                .filter(employee -> employeeId == null || !employee.getId().equals(employeeId))
                .ifPresent(employee -> { throw conflict("employee_code_exists", "Employee code is already used in this organization"); });
    }

    private Map<String, Object> membershipSnapshot(OrganizationMembership membership) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("userId", membership.getUserId());
        value.put("role", membership.getRole().name());
        value.put("status", membership.getStatus().name());
        return value;
    }
}
