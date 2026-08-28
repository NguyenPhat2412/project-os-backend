package com.projectos.backend.organization.onboarding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public class OnboardingTokenService {
    private final SecureRandom random = new SecureRandom();

    public String issue() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public String digest(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return "";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Token digest algorithm is unavailable", exception);
        }
    }

    public boolean matches(String rawToken, String expectedDigest) {
        return MessageDigest.isEqual(digest(rawToken).getBytes(StandardCharsets.US_ASCII),
                (expectedDigest == null ? "" : expectedDigest).getBytes(StandardCharsets.US_ASCII));
    }

    public boolean isAllowedTargetRole(String role) {
        return "ROLE_EMPLOYEE".equals(role) || "ROLE_DEPT_LEAD".equals(role);
    }
}
