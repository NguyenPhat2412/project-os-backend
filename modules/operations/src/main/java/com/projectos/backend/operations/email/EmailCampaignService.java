package com.projectos.backend.operations.email;

import static com.projectos.backend.operations.email.EmailCampaignContracts.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.organization.OrganizationDirectory;
import com.projectos.backend.platform.organization.OrganizationPermissionPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailCampaignService {
    public static final String TEMPLATE_READ = "EMAIL_TEMPLATE_READ";
    public static final String TEMPLATE_MANAGE = "EMAIL_TEMPLATE_MANAGE";
    public static final String CAMPAIGN_PREVIEW = "EMAIL_CAMPAIGN_PREVIEW";
    public static final String CAMPAIGN_CREATE = "EMAIL_CAMPAIGN_CREATE";
    public static final String CAMPAIGN_QUEUE = "EMAIL_CAMPAIGN_QUEUE";
    public static final String CAMPAIGN_CANCEL = "EMAIL_CAMPAIGN_CANCEL";
    public static final String CAMPAIGN_READ = "EMAIL_CAMPAIGN_READ";
    public static final String CAMPAIGN_RETRY = "EMAIL_CAMPAIGN_RETRY";
    private static final String EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private static final String TEMPLATE_COLUMNS = "id, organization_id, code, title, subject, body_html, allowed_variables, status, updated_at";
    private static final String CAMPAIGN_COLUMNS = "id, organization_id, subject, template_id, status, total_recipients, sent_count, failed_count, preview_hash, idempotency_key, created_at, queued_at, completed_at";
    private final JdbcTemplate jdbc;
    private final EmailTemplateSanitizer sanitizer;
    private final ObjectProvider<OrganizationDirectory> organizations;
    private final ObjectProvider<OrganizationPermissionPort> permissions;
    private final ObjectMapper json = new ObjectMapper();

    public EmailCampaignService(JdbcTemplate jdbc, EmailTemplateSanitizer sanitizer) {
        this(jdbc, sanitizer, null, null);
    }
    @Autowired
    public EmailCampaignService(JdbcTemplate jdbc, EmailTemplateSanitizer sanitizer,
                                ObjectProvider<OrganizationDirectory> organizations,
                                ObjectProvider<OrganizationPermissionPort> permissions) {
        this.jdbc = jdbc; this.sanitizer = sanitizer; this.organizations = organizations; this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates(UUID organizationId, UUID actor, boolean root) {
        requirePermission(organizationId, actor, root, TEMPLATE_READ);
        return jdbc.queryForList("select " + TEMPLATE_COLUMNS + " from public.email_templates where organization_id=? order by updated_at desc", organizationId)
                .stream().map(row -> template(row)).toList();
    }

    @Transactional
    public TemplateView createTemplate(UUID organizationId, UUID actor, boolean root, TemplateRequest request) {
        requirePermission(organizationId, actor, root, TEMPLATE_MANAGE); validateTemplate(request);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.email_templates (id,organization_id,code,title,subject,body_html,allowed_variables,status,created_by,updated_by) values (?,?,?,?,?,?,?,?,?,?)", id, organizationId, clean(request.code(), "email_template_code_required", 80), clean(request.title(), "email_template_title_required", 160), clean(request.subject(), "email_subject_required", 255), sanitizer.sanitize(request.bodyHtml()), jsonText(request.allowedVariables()), status(request.status()), actor, actor);
        return template(jdbc.queryForMap("select id, organization_id, code, title, subject, body_html, allowed_variables, status, updated_at from public.email_templates where id=? and organization_id=?", id, organizationId));
    }

    @Transactional
    public TemplateView updateTemplate(UUID organizationId, UUID id, UUID actor, boolean root, TemplateRequest request) {
        requirePermission(organizationId, actor, root, TEMPLATE_MANAGE); validateTemplate(request);
        int changed = jdbc.update("update public.email_templates set code=?,title=?,subject=?,body_html=?,allowed_variables=?,status=?,updated_by=?,updated_at=now() where id=? and organization_id=?", clean(request.code(), "email_template_code_required", 80), clean(request.title(), "email_template_title_required", 160), clean(request.subject(), "email_subject_required", 255), sanitizer.sanitize(request.bodyHtml()), jsonText(request.allowedVariables()), status(request.status()), actor, id, organizationId);
        if (changed == 0) throw notFound("email_template_not_found", "Không tìm thấy mẫu email.");
        return template(jdbc.queryForMap("select id, organization_id, code, title, subject, body_html, allowed_variables, status, updated_at from public.email_templates where id=? and organization_id=?", id, organizationId));
    }

    @Transactional
    public void deleteTemplate(UUID organizationId, UUID id, UUID actor, boolean root) {
        requirePermission(organizationId, actor, root, TEMPLATE_MANAGE);
        if (jdbc.update("update public.email_templates set status='INACTIVE',updated_by=?,updated_at=now() where id=? and organization_id=?", actor, id, organizationId) == 0) throw notFound("email_template_not_found", "Không tìm thấy mẫu email.");
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(UUID organizationId, UUID actor, boolean root, PreviewRequest request) {
        requirePermission(organizationId, actor, root, CAMPAIGN_PREVIEW); validateContent(request.subject(), request.bodyHtml());
        List<Map<String,Object>> rows = recipients(organizationId, request.employeeIds(), request.departmentId());
        List<RecipientPreview> valid = new ArrayList<>(), excluded = new ArrayList<>();
        for (Map<String,Object> row : rows) {
            UUID id = (UUID) row.get("id"); String name = text(row.get("full_name")); String code = text(row.get("code")); String email = text(row.get("email")); String status = text(row.get("status"));
            if (!"ACTIVE".equalsIgnoreCase(status) || Boolean.TRUE.equals(row.get("is_deleted"))) excluded.add(new RecipientPreview(id,name,code,email,"Nhân sự không còn hoạt động."));
            else if (!email.matches(EMAIL)) excluded.add(new RecipientPreview(id,name,code,email,"Chưa có địa chỉ email hợp lệ."));
            else valid.add(new RecipientPreview(id,name,code,email,""));
        }
        return new PreviewResponse(List.copyOf(valid), List.copyOf(excluded), hash(valid));
    }

    @Transactional
    public CampaignView createCampaign(UUID organizationId, UUID actor, boolean root, CampaignRequest request) {
        requirePermission(organizationId, actor, root, CAMPAIGN_CREATE); validateContent(request.subject(), request.bodyHtml());
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) throw bad("idempotency_key_required", "Thiếu mã chống gửi trùng.");
        var existing = jdbc.query("select " + CAMPAIGN_COLUMNS + " from public.email_campaigns where organization_id=? and idempotency_key=?", this::campaign, organizationId, request.idempotencyKey());
        if (!existing.isEmpty()) return existing.getFirst();
        PreviewResponse preview = preview(organizationId, actor, root, new PreviewRequest(request.subject(),request.bodyHtml(),request.templateId(),request.employeeIds(),request.departmentId()));
        if (request.previewHash() == null || !request.previewHash().equals(preview.snapshotHash())) throw bad("email_preview_expired", "Danh sách người nhận đã thay đổi, vui lòng xem trước lại.");
        if (preview.validRecipients().isEmpty()) throw bad("email_campaign_empty", "Không có nhân sự hợp lệ để gửi email.");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.email_campaigns (id,organization_id,created_by,subject,body_html,template_id,audience_filter,preview_hash,status,total_recipients,idempotency_key) values (?,?,?,?,?,?,?,?,?,?,?)", id,organizationId,actor,request.subject().trim(),sanitizer.sanitize(request.bodyHtml()),request.templateId(), audience(request),preview.snapshotHash(),"DRAFT",preview.validRecipients().size(),request.idempotencyKey());
        for (RecipientPreview recipient : preview.validRecipients()) jdbc.update("insert into public.email_campaign_recipients (id,campaign_id,organization_id,employee_id,employee_name_snapshot,employee_code_snapshot,email_snapshot) values (?,?,?,?,?,?,?)", UUID.randomUUID(),id,organizationId,recipient.employeeId(),recipient.employeeName(),recipient.employeeCode(),recipient.email());
        return getCampaign(organizationId,id,actor,root);
    }

    @Transactional(readOnly = true)
    public List<CampaignView> campaigns(UUID organizationId, UUID actor, boolean root) { requirePermission(organizationId,actor,root,CAMPAIGN_READ); return jdbc.query("select " + CAMPAIGN_COLUMNS + " from public.email_campaigns where organization_id=? order by created_at desc", this::campaign, organizationId); }
    @Transactional(readOnly = true)
    public CampaignView getCampaign(UUID organizationId, UUID id, UUID actor, boolean root) { requirePermission(organizationId,actor,root,CAMPAIGN_READ); try { return jdbc.queryForObject("select " + CAMPAIGN_COLUMNS + " from public.email_campaigns where organization_id=? and id=?", this::campaign, organizationId,id); } catch (Exception e) { throw notFound("email_campaign_not_found","Không tìm thấy chiến dịch email."); } }
    @Transactional(readOnly = true)
    public List<Map<String,Object>> campaignRecipients(UUID organizationId, UUID id, UUID actor, boolean root) { getCampaign(organizationId,id,actor,root); return jdbc.queryForList("select id,employee_id,employee_name_snapshot,employee_code_snapshot,email_snapshot,status,attempts,friendly_error,sent_at from public.email_campaign_recipients where organization_id=? and campaign_id=? order by employee_name_snapshot",organizationId,id); }
    @Transactional
    public CampaignView queue(UUID organizationId, UUID id, UUID actor, boolean root, String previewHash) { requirePermission(organizationId,actor,root,CAMPAIGN_QUEUE); int changed=jdbc.update("update public.email_campaigns set status='QUEUED',queued_at=now() where id=? and organization_id=? and status='DRAFT' and preview_hash=? and total_recipients>0",id,organizationId,previewHash); if(changed==0) throw bad("email_campaign_cannot_queue","Chiến dịch không thể xếp hàng hoặc bản xem trước đã hết hạn."); return getCampaign(organizationId,id,actor,root); }
    @Transactional
    public CampaignView cancel(UUID organizationId, UUID id, UUID actor, boolean root) { requirePermission(organizationId,actor,root,CAMPAIGN_CANCEL); if(jdbc.update("update public.email_campaigns set status='CANCELLED' where id=? and organization_id=? and status in ('DRAFT','QUEUED')",id,organizationId)==0) throw bad("email_campaign_cannot_cancel","Chiến dịch không thể hủy ở trạng thái hiện tại."); return getCampaign(organizationId,id,actor,root); }
    @Transactional
    public CampaignView retryFailed(UUID organizationId, UUID id, UUID actor, boolean root) { requirePermission(organizationId,actor,root,CAMPAIGN_RETRY); jdbc.update("update public.email_campaign_recipients set status='PENDING',friendly_error=null where campaign_id=? and organization_id=? and status='FAILED'",id,organizationId); jdbc.update("update public.email_campaigns set status='QUEUED',failed_count=0 where id=? and organization_id=? and status in ('FAILED','PARTIAL')",id,organizationId); return getCampaign(organizationId,id,actor,root); }

    private List<Map<String,Object>> recipients(UUID org,List<UUID> ids,UUID dept){ StringBuilder sql=new StringBuilder("select id,full_name,coalesce(code,'') code,coalesce(email,'') email,status,coalesce(is_deleted,false) is_deleted from public.employees where organization_id=?"); List<Object> args=new ArrayList<>(List.of(org)); if(dept!=null){sql.append(" and department_id=?");args.add(dept);} if(ids!=null&&!ids.isEmpty()){sql.append(" and id in (").append("?,".repeat(ids.size()).replaceAll(",$","")).append(")");args.addAll(ids);} return jdbc.queryForList(sql.toString(),args.toArray()); }
    private void validateTemplate(TemplateRequest r){ if(r==null) throw bad("email_template_required","Thông tin mẫu email là bắt buộc."); String body=sanitizer.sanitize(r.bodyHtml()); sanitizer.validateVariables(r.subject(),r.allowedVariables()); sanitizer.validateVariables(body,r.allowedVariables()); status(r.status()); clean(r.code(), "email_template_code_required", 80); clean(r.title(), "email_template_title_required", 160); clean(r.subject(), "email_subject_required", 255); }
    private void validateContent(String subject,String body){ if(subject==null||subject.isBlank()||subject.length()>255) throw bad("email_subject_invalid","Tiêu đề email không hợp lệ."); sanitizer.sanitize(body); }
    private String status(String v){ String result=v==null||v.isBlank()?"ACTIVE":v.trim().toUpperCase(); if(!List.of("ACTIVE","INACTIVE").contains(result)) throw bad("email_template_status_invalid","Trạng thái mẫu email không hợp lệ."); return result; }
    private String clean(String v,String code,int max){ if(v==null||v.isBlank()||v.length()>max) throw bad(code,"Thông tin email không hợp lệ."); return v.trim(); }
    private String jsonText(Object value){ try{return json.writeValueAsString(value==null?List.of():value);}catch(JsonProcessingException e){throw bad("email_template_variables_invalid","Danh sách biến mẫu không hợp lệ.");} }
    private String audience(CampaignRequest r){try{return json.writeValueAsString(Map.of("employeeIds",r.employeeIds()==null?List.of():r.employeeIds(),"departmentId",r.departmentId()==null?"":r.departmentId()));}catch(JsonProcessingException e){return "{}";}}
    private String hash(List<RecipientPreview> r){try{var md=MessageDigest.getInstance("SHA-256");return java.util.HexFormat.of().formatHex(md.digest(r.stream().map(v->v.employeeId()+":"+v.email()).sorted().reduce("",(a,b)->a+"|"+b).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private TemplateView template(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return template(Map.of("id",rs.getObject("id"),"organization_id",rs.getObject("organization_id"),"code",rs.getString("code"),"title",rs.getString("title"),"subject",rs.getString("subject"),"body_html",rs.getString("body_html"),"allowed_variables",rs.getObject("allowed_variables"),"status",rs.getString("status"),"updated_at",rs.getTimestamp("updated_at")));}
    private TemplateView template(Map<String,Object> r){return new TemplateView((UUID)r.get("id"),(UUID)r.get("organization_id"),text(r.get("code")),text(r.get("title")),text(r.get("subject")),text(r.get("body_html")),variables(r.get("allowed_variables")),text(r.get("status")),toInstant(r.get("updated_at")));}
    private List<String> variables(Object value){
        if(value==null)return List.of();
        try { return json.readValue(String.valueOf(value), json.getTypeFactory().constructCollectionType(List.class, String.class)); }
        catch(Exception e){ return List.of(); }
    }
    private CampaignView campaign(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new CampaignView((UUID)rs.getObject("id"),(UUID)rs.getObject("organization_id"),rs.getString("subject"),(UUID)rs.getObject("template_id"),rs.getString("status"),rs.getInt("total_recipients"),rs.getInt("sent_count"),rs.getInt("failed_count"),rs.getString("preview_hash"),rs.getString("idempotency_key"),toInstant(rs.getTimestamp("created_at")),toInstant(rs.getTimestamp("queued_at")),toInstant(rs.getTimestamp("completed_at")));}
    private void requireAccess(UUID org,UUID actor,boolean root){if(root)return; if(organizations==null||organizations.getIfAvailable()==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"organization_directory_unavailable","Hệ thống chưa sẵn sàng."); var access=organizations.getIfAvailable().access(org,actor); String role=access==null?"":String.valueOf(access.role()).toUpperCase(); if(!List.of("OWNER","ADMIN","HR","SUPER_ADMIN","PLATFORM_ADMIN","HR_MANAGER").contains(role))throw new ApiException(HttpStatus.FORBIDDEN,"email_campaign_access_denied","Bạn không có quyền quản lý email nội bộ.");}
    private void requirePermission(UUID org, UUID actor, boolean root, String key) {
        if (root) return;
        if (permissions != null && permissions.getIfAvailable() != null) {
            permissions.getIfAvailable().requirePermission(org, actor, false, key);
            return;
        }
        requireAccess(org, actor, false);
    }
    private String text(Object v){return v==null?"":String.valueOf(v);}
    private Instant toInstant(Object v){return v instanceof Timestamp t?t.toInstant():v instanceof Instant i?i:null;}
    private ApiException bad(String c,String m){return new ApiException(HttpStatus.BAD_REQUEST,c,m);} private ApiException notFound(String c,String m){return new ApiException(HttpStatus.NOT_FOUND,c,m);}
}
