package com.projectos.backend.identity.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.projectos.backend.platform.api.ApiException;

/** Redis fixed-window limiter for cookie-based authentication endpoints. */
@Service
public class AuthRateLimitService {
    private final StringRedisTemplate redis;
    private final boolean enabled;

    AuthRateLimitService(StringRedisTemplate redis,
                         @Value("${app.security.rate-limit.enabled:false}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    public void checkLogin(String email, String ipAddress) {
        check("login:" + digest(email.trim().toLowerCase() + "|" + ipAddress), 5, Duration.ofMinutes(15));
    }

    public void checkRegister(String email, String ipAddress) {
        check("register:" + digest(email.trim().toLowerCase() + "|" + ipAddress), 5, Duration.ofHours(1));
    }

    public void checkRefresh(String ipAddress) {
        check("refresh:" + digest(ipAddress), 10, Duration.ofMinutes(5));
    }

    private void check(String suffix, long limit, Duration window) {
        if (!enabled) return;
        String key = "project-os:rate-limit:" + suffix;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) redis.expire(key, window);
            if (count != null && count > limit) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded",
                        "Too many authentication requests");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "rate_limit_unavailable",
                    "Authentication protection is temporarily unavailable");
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
