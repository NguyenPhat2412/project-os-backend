package com.projectos.backend.operations.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Sends only already-validated internal recipient snapshots. */
@Component
@ConditionalOnProperty(name = "app.email.worker.enabled", havingValue = "true")
public class EmailDeliveryWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final int batchSize;
    private final int maxAttempts;
    private final String username;
    private final String password;

    public EmailDeliveryWorker(JdbcTemplate jdbc,
            @Value("${app.email.worker.batch-size:50}") int batchSize,
            @Value("${app.email.worker.max-attempts:3}") int maxAttempts,
            @Value("${app.email.worker.username:}") String username,
            @Value("${app.email.worker.password:}") String password) {
        this.jdbc=jdbc; this.batchSize=Math.max(1,batchSize); this.maxAttempts=Math.max(1,maxAttempts); this.username=username; this.password=password;
    }

    @Scheduled(fixedDelayString = "${app.email.worker.interval-ms:10000}")
    @Transactional
    public void deliverPending() {
        List<Map<String,Object>> pending = jdbc.queryForList("select r.id,r.campaign_id,r.organization_id,r.email_snapshot,r.employee_name_snapshot,c.subject,c.body_html,s.settings::text settings from public.email_campaign_recipients r join public.email_campaigns c on c.id=r.campaign_id join public.organization_settings s on s.organization_id=r.organization_id where r.status='PENDING' and c.status in ('QUEUED','SENDING') order by c.created_at,r.id limit ?", batchSize);
        for (Map<String,Object> row : pending) deliver(row);
    }

    private void deliver(Map<String,Object> row) {
        UUID recipient=(UUID)row.get("id");
        if (jdbc.update("update public.email_campaign_recipients set status='SENDING',attempts=attempts+1 where id=? and status='PENDING'",recipient)!=1) return;
        String trace=UUID.randomUUID().toString();
        try {
            JsonNode settings=json.readTree(String.valueOf(row.get("settings"))).path("notifications");
            String host=settings.path("smtpHost").asText("").trim(); int port=settings.path("smtpPort").asInt(587); String sender=settings.path("smtpSenderAddress").asText("").trim();
            if(host.isBlank()||sender.isBlank()||username.isBlank()||password.isBlank()) throw new IllegalStateException("mail configuration unavailable");
            JavaMailSenderImpl senderClient=new JavaMailSenderImpl(); senderClient.setHost(host); senderClient.setPort(port); senderClient.setUsername(username); senderClient.setPassword(password);
            var props=senderClient.getJavaMailProperties(); props.put("mail.smtp.auth","true"); props.put("mail.smtp.starttls.enable", "TLS".equalsIgnoreCase(settings.path("smtpEncryption").asText("TLS")) || "STARTTLS".equalsIgnoreCase(settings.path("smtpEncryption").asText("TLS"))); props.put("mail.smtp.ssl.enable", "SSL".equalsIgnoreCase(settings.path("smtpEncryption").asText("TLS"))); props.put("mail.smtp.connectiontimeout","10000"); props.put("mail.smtp.timeout","15000");
            MimeMessage message=senderClient.createMimeMessage(); MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8"); helper.setFrom(sender,settings.path("smtpSenderName").asText("")); helper.setTo(String.valueOf(row.get("email_snapshot"))); helper.setSubject(String.valueOf(row.get("subject"))); helper.setText(String.valueOf(row.get("body_html")),true); senderClient.send(message);
            jdbc.update("update public.email_campaign_recipients set status='SENT',sent_at=now(),friendly_error=null where id=?",recipient); jdbc.update("insert into public.email_delivery_attempts (id,recipient_id,organization_id,status,trace_id) values (?,?,?,?,?)",UUID.randomUUID(),recipient,row.get("organization_id"),"SENT",trace);
        } catch(Exception ignored) {
            Integer attempts=jdbc.queryForObject("select attempts from public.email_campaign_recipients where id=?",Integer.class,recipient); boolean exhausted=attempts!=null&&attempts>=maxAttempts; String status=exhausted?"FAILED":"PENDING"; jdbc.update("update public.email_campaign_recipients set status=?,friendly_error=? where id=?",status,exhausted?"Không thể gửi email sau nhiều lần thử.":"Chưa gửi được, hệ thống sẽ thử lại.",recipient); jdbc.update("insert into public.email_delivery_attempts (id,recipient_id,organization_id,status,trace_id,sanitized_error) values (?,?,?,?,?,?)",UUID.randomUUID(),recipient,row.get("organization_id"),exhausted?"FAILED":"RETRYING",trace,exhausted?"Gửi email thất bại.":"Lỗi tạm thời khi gửi email.");
        }
        jdbc.update("update public.email_campaigns c set sent_count=(select count(*) from public.email_campaign_recipients r where r.campaign_id=c.id and r.status='SENT'),failed_count=(select count(*) from public.email_campaign_recipients r where r.campaign_id=c.id and r.status='FAILED'),status=case when not exists(select 1 from public.email_campaign_recipients r where r.campaign_id=c.id and r.status in ('PENDING','SENDING')) then case when exists(select 1 from public.email_campaign_recipients r where r.campaign_id=c.id and r.status='FAILED') then 'PARTIAL' else 'COMPLETED' end else 'SENDING' end,completed_at=case when not exists(select 1 from public.email_campaign_recipients r where r.campaign_id=c.id and r.status in ('PENDING','SENDING')) then now() else c.completed_at end where c.id=?",row.get("campaign_id"));
    }
}
