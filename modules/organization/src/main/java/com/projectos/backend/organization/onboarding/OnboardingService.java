package com.projectos.backend.organization.onboarding;

import com.projectos.backend.organization.domain.OrganizationPermissionService;
import com.projectos.backend.organization.web.OnboardingController;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.api.PageResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
    private final JdbcTemplate jdbc;
    private final OrganizationPermissionService permissions;
    private final OnboardingTokenService tokens;
    private final String publicWebUrl;

    public OnboardingService(JdbcTemplate jdbc, OrganizationPermissionService permissions,
                             @Value("${app.public-web-url:http://localhost:3000}") String publicWebUrl) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.tokens = new OnboardingTokenService();
        this.publicWebUrl = publicWebUrl.replaceAll("/+$", "");
    }

    @Transactional
    public OnboardingController.InvitationCreated createInvitation(UUID organizationId,
            OnboardingController.InvitationRequest request, UUID actor, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actor, root);
        String role = normalizeRole(request.targetRole());
        int hours = request.expiresInHours() == null ? 72 : request.expiresInHours();
        if (hours < 1 || hours > 720) throw bad("onboarding_invitation_expiry_invalid", "Thời hạn đường link không hợp lệ.");
        String email = clean(request.inviteEmail());
        UUID departmentId = scopedReference(request.departmentId(), organizationId, "departments", "department_not_found");
        UUID positionId = scopedReference(request.positionId(), organizationId, "organization_positions", "position_not_found");
        String rawToken = tokens.issue();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(hours));
        UUID id = UUID.randomUUID();
        jdbc.update("insert into onboarding_invitations (id, organization_id, token_hash, invite_email, department_id, position_id, target_role, expires_at, created_by, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                id, organizationId, tokens.digest(rawToken), email, departmentId, positionId, role, expiresAt, actor);
        return new OnboardingController.InvitationCreated(id, publicWebUrl + "/onboarding?token=" + rawToken, expiresAt,
                "ACTIVE");
    }

    @Transactional
    public OnboardingController.InvitationView publicInvitation(String rawToken) {
        return loadInvitation(rawToken, false);
    }

    private OnboardingController.InvitationView loadInvitation(String rawToken, boolean lock) {
        if (rawToken == null || rawToken.isBlank()) throw invalidToken();
        jdbc.execute("select public.expire_onboarding_invitations()");
        String lockClause = lock ? " for update of i" : "";
        return jdbc.query("select i.id, i.status, i.expires_at, i.invite_email, i.target_role, d.name department_name, p.title position_title from onboarding_invitations i left join departments d on d.id = i.department_id left join organization_positions p on p.id = i.position_id where i.token_hash = ? and i.status = 'ACTIVE' and i.expires_at > now()" + lockClause,
                (rs, row) -> new OnboardingController.InvitationView(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getObject("expires_at", Instant.class), rs.getString("invite_email"), rs.getString("target_role"),
                        rs.getString("department_name"), rs.getString("position_title")), tokens.digest(rawToken))
                .stream().findFirst().orElseThrow(this::invalidToken);
    }

    @Transactional
    public OnboardingController.SubmissionResult submit(String rawToken, OnboardingController.SubmissionRequest request) {
        if (!Boolean.TRUE.equals(request.consent())) throw bad("onboarding_consent_required", "Vui lòng xác nhận thông tin trước khi gửi.");
        OnboardingController.InvitationView invitation = invitationForUpdate(rawToken);
        LocalDate birthDate = date(request.birthDate(), "birthDate");
        LocalDate idIssueDate = optionalDate(request.idIssueDate(), "idIssueDate");
        UUID id = UUID.randomUUID();
        String reference = "HS-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        try {
            jdbc.update("insert into onboarding_requests (id, organization_id, invitation_id, reference_code, full_name, birth_date, gender, citizen_id, id_issue_date, id_issue_place, birth_place, marital_status, ethnicity, religion, phone, email, permanent_address, current_address, bank_name, bank_account_number, bank_account_holder, tax_code, social_insurance_number, emergency_contact_name, emergency_contact_relationship, emergency_contact_phone, education_level, major_field, personal_notes, consent_at) select ?, organization_id, id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now() from onboarding_invitations where id = ?",
                    id, reference, required(request.fullName(), "fullName"), birthDate, clean(request.gender()), required(request.citizenId(), "citizenId"), idIssueDate,
                    clean(request.idIssuePlace()), clean(request.birthPlace()), clean(request.maritalStatus()), clean(request.ethnicity()), clean(request.religion()),
                    required(request.phone(), "phone"), requiredEmail(request.email()), clean(request.permanentAddress()), clean(request.currentAddress()),
                    clean(request.bankName()), clean(request.bankAccountNumber()), clean(request.bankAccountHolder()), clean(request.taxCode()),
                    clean(request.socialInsuranceNumber()), clean(request.emergencyContactName()), clean(request.emergencyContactRelationship()), clean(request.emergencyContactPhone()),
                    clean(request.educationLevel()), clean(request.majorField()), clean(request.personalNotes()), invitation.id());
            if (jdbc.update("update onboarding_invitations set status = 'USED', used_at = now(), updated_at = now() where id = ? and status = 'ACTIVE'", invitation.id()) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "onboarding_already_submitted", "Đường link này đã được sử dụng hoặc hồ sơ đã tồn tại.");
            }
            return new OnboardingController.SubmissionResult(reference, "PENDING", "Hồ sơ đã được gửi và đang chờ công ty tiếp nhận.");
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "onboarding_already_submitted", "Đường link này đã được sử dụng hoặc hồ sơ đã tồn tại.");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<OnboardingController.RequestView> requests(UUID organizationId, int page, int size, UUID actor, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actor, root);
        int safePage = Math.max(page, 0), safeSize = Math.min(Math.max(size, 1), 100), offset = safePage * safeSize;
        long total = jdbc.queryForObject("select count(*) from onboarding_requests where organization_id = ?", Long.class, organizationId);
        List<OnboardingController.RequestView> data = jdbc.query("select r.id, r.reference_code, r.status, r.full_name, r.email, r.phone, r.birth_date, r.citizen_id, r.submitted_at, r.rejection_reason, r.employee_id, d.name department_name, p.title position_title from onboarding_requests r join onboarding_invitations i on i.id = r.invitation_id left join departments d on d.id = i.department_id left join organization_positions p on p.id = i.position_id where r.organization_id = ? order by r.submitted_at desc limit ? offset ?",
                (rs, row) -> new OnboardingController.RequestView(rs.getObject("id", UUID.class), rs.getString("reference_code"), rs.getString("status"),
                        rs.getString("full_name"), rs.getString("email"), rs.getString("phone"), rs.getObject("birth_date", LocalDate.class).toString(),
                        rs.getString("citizen_id"), rs.getObject("submitted_at", Instant.class), rs.getString("rejection_reason"), rs.getObject("employee_id", UUID.class),
                        rs.getString("department_name"), rs.getString("position_title")), organizationId, safeSize, offset);
        return PageResponse.of(data, safePage, safeSize, total, (int) Math.ceil((double) total / safeSize));
    }

    @Transactional(readOnly = true)
    public OnboardingController.RequestSummary summary(UUID organizationId, UUID actor, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actor, root);
        Long pending = jdbc.queryForObject("select count(*) from onboarding_requests where organization_id = ? and status = 'PENDING'", Long.class, organizationId);
        return new OnboardingController.RequestSummary(pending == null ? 0 : pending);
    }

    @Transactional
    public OnboardingController.RequestView approve(UUID organizationId, UUID requestId, UUID actor, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actor, root);
        OnboardingController.RequestView request = request(organizationId, requestId);
        if (!"PENDING".equals(request.status())) throw conflict("onboarding_request_already_reviewed", "Hồ sơ này đã được xử lý.");
        UUID employeeId = UUID.randomUUID();
        String employeeCode = "NV-" + employeeId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update("insert into employees (id, organization_id, department_id, code, full_name, email, phone, title, citizen_id, birth_date, status, is_deleted, created_at, updated_at) select ?, i.organization_id, i.department_id, ?, r.full_name, r.email, r.phone, p.title, r.citizen_id, r.birth_date::text, 'ACTIVE', false, now(), now() from onboarding_requests r join onboarding_invitations i on i.id = r.invitation_id left join organization_positions p on p.id = i.position_id where r.id = ? and r.organization_id = ?",
                employeeId, employeeCode, requestId, organizationId);
        jdbc.update("update onboarding_requests set status = 'APPROVED', reviewed_at = now(), reviewed_by = ?, employee_id = ?, updated_at = now() where id = ? and organization_id = ? and status = 'PENDING'",
                actor, employeeId, requestId, organizationId);
        return request(organizationId, requestId);
    }

    @Transactional
    public void reject(UUID organizationId, UUID requestId, String reason, UUID actor, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actor, root);
        if (jdbc.update("update onboarding_requests set status = 'REJECTED', rejection_reason = ?, reviewed_at = now(), reviewed_by = ?, updated_at = now() where id = ? and organization_id = ? and status = 'PENDING'",
                clean(reason), actor, requestId, organizationId) == 0) throw conflict("onboarding_request_already_reviewed", "Hồ sơ này đã được xử lý.");
    }

    @Transactional
    public void revoke(UUID organizationId, UUID invitationId, UUID actor, boolean root) {
        permissions.requireOrganizationAdmin(organizationId, actor, root);
        if (jdbc.update("update onboarding_invitations set status = 'REVOKED', revoked_at = now(), updated_at = now() where id = ? and organization_id = ? and status = 'ACTIVE'", invitationId, organizationId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "onboarding_invitation_not_found", "Không tìm thấy đường link đang hoạt động.");
        }
    }

    private OnboardingController.InvitationView invitationForUpdate(String rawToken) {
        return loadInvitation(rawToken, true);
    }

    private OnboardingController.RequestView request(UUID organizationId, UUID requestId) {
        return jdbc.query("select r.id, r.reference_code, r.status, r.full_name, r.email, r.phone, r.birth_date, r.citizen_id, r.submitted_at, r.rejection_reason, r.employee_id, d.name department_name, p.title position_title from onboarding_requests r join onboarding_invitations i on i.id = r.invitation_id left join departments d on d.id = i.department_id left join organization_positions p on p.id = i.position_id where r.id = ? and r.organization_id = ?",
                (rs, row) -> new OnboardingController.RequestView(rs.getObject("id", UUID.class), rs.getString("reference_code"), rs.getString("status"),
                        rs.getString("full_name"), rs.getString("email"), rs.getString("phone"), rs.getObject("birth_date", LocalDate.class).toString(),
                        rs.getString("citizen_id"), rs.getObject("submitted_at", Instant.class), rs.getString("rejection_reason"), rs.getObject("employee_id", UUID.class),
                        rs.getString("department_name"), rs.getString("position_title")), requestId, organizationId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "onboarding_request_not_found", "Không tìm thấy hồ sơ tiếp nhận."));
    }

    private String normalizeRole(String raw) {
        String role = raw == null || raw.isBlank() ? "ROLE_EMPLOYEE" : raw.trim().toUpperCase(Locale.ROOT);
        if (!tokens.isAllowedTargetRole(role)) throw bad("onboarding_target_role_invalid", "Vai trò tiếp nhận không hợp lệ.");
        return role;
    }

    private UUID scopedReference(UUID id, UUID organizationId, String table, String code) {
        if (id == null) return null;
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where id = ? and organization_id = ?", Integer.class, id, organizationId);
        if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, code, "Dữ liệu lựa chọn không còn tồn tại.");
        return id;
    }

    private LocalDate date(String value, String field) {
        String normalized = required(value, field);
        try { return LocalDate.parse(normalized); } catch (DateTimeParseException exception) { throw bad("invalid_date", "Ngày nhập chưa đúng định dạng."); }
    }

    private LocalDate optionalDate(String value, String field) {
        return value == null || value.isBlank() ? null : date(value, field);
    }

    private String required(String value, String field) {
        String result = clean(value);
        if (result == null || result.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", "Vui lòng điền đầy đủ thông tin bắt buộc.");
        return result;
    }

    private String requiredEmail(String value) {
        String result = required(value, "email");
        if (!result.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw bad("validation_failed", "Email chưa đúng định dạng.");
        return result.toLowerCase(Locale.ROOT);
    }

    private String clean(String value) { return value == null ? null : value.trim(); }
    private ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private ApiException invalidToken() { return new ApiException(HttpStatus.NOT_FOUND, "onboarding_invitation_invalid", "Đường link kê khai không hợp lệ hoặc đã hết hạn."); }
}
