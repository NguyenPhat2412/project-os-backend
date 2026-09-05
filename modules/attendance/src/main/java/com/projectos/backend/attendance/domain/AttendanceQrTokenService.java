package com.projectos.backend.attendance.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AttendanceQrTokenService {
    private final long ttlSeconds;
    private final byte[] secret;

    AttendanceQrTokenService(@Value("${app.attendance.qr-ttl-seconds:300}") long ttlSeconds,
                             @Value("${app.attendance.qr-secret:project-os-attendance-local-secret}") String secret) {
        this.ttlSeconds = ttlSeconds;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    Token issue(UUID organizationId, UUID employeeId, String employeeCode) {
        Instant now = Instant.now();
        String payload = String.join(".", "ATTENDANCE_QR", organizationId.toString(), employeeId.toString(),
                Long.toString(now.plusSeconds(ttlSeconds).getEpochSecond()), employeeCode == null ? "" : employeeCode);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + sign(payload);
        return new Token(value, ttlSeconds);
    }

    Token issueSite(UUID organizationId) {
        Instant now = Instant.now();
        long siteTtlSeconds = Math.min(ttlSeconds, 60);
        String payload = String.join(".", "ATTENDANCE_SITE_QR", organizationId.toString(),
                Long.toString(now.plusSeconds(siteTtlSeconds).getEpochSecond()));
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + sign(payload);
        return new Token(value, siteTtlSeconds);
    }

    boolean matchesSite(String value, UUID organizationId) {
        if (value == null || value.isBlank()) return false;
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2 || !constantTime(parts[1], sign(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)))) return false;
            String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\.", -1);
            return payload.length == 3 && "ATTENDANCE_SITE_QR".equals(payload[0])
                    && organizationId.toString().equals(payload[1])
                    && Long.parseLong(payload[2]) >= Instant.now().getEpochSecond();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    boolean matches(String value, UUID organizationId, UUID employeeId) {
        if (value == null || value.isBlank()) return false;
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2 || !constantTime(parts[1], sign(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)))) return false;
            String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] payload = decoded.split("\\.", -1);
            if (payload.length == 5 && "ATTENDANCE_QR".equals(payload[0])
                    && organizationId.toString().equals(payload[1]) && employeeId.toString().equals(payload[2])
                    && Long.parseLong(payload[3]) >= Instant.now().getEpochSecond()) {
                return true;
            }
            if (payload.length == 3 && "ATTENDANCE_SITE_QR".equals(payload[0])
                    && organizationId.toString().equals(payload[1])
                    && Long.parseLong(payload[2]) >= Instant.now().getEpochSecond()) {
                return true;
            }
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String sign(String value) { try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception ex){ throw new IllegalStateException("Unable to sign attendance token",ex); } }
    private boolean constantTime(String left,String right){return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),right.getBytes(StandardCharsets.UTF_8));}

    public record Token(String value, long expiresInSeconds) {}
}
