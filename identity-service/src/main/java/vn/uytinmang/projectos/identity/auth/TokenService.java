package vn.uytinmang.projectos.identity.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.identity.user.UserAccount;
import vn.uytinmang.projectos.platform.api.ApiException;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens,
                        @Value("${app.jwt.access-ttl-minutes:15}") long accessMinutes,
                        @Value("${app.jwt.refresh-ttl-days:14}") long refreshDays) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    @Transactional
    public SessionTokens issue(UserAccount user) {
        String rawRefresh = randomToken();
        refreshTokens.save(new RefreshToken(hash(rawRefresh), user, Instant.now().plus(refreshTtl)));
        return new SessionTokens(access(user), rawRefresh, accessTtl.toSeconds(), refreshTtl.toSeconds());
    }

    @Transactional
    public RotatedSession rotate(String rawRefresh) {
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawRefresh))
                .orElseThrow(() -> invalidRefresh());
        if (current.getRevokedAt() != null || current.getExpiresAt().isBefore(Instant.now())) {
            throw invalidRefresh();
        }
        current.revoke();
        return new RotatedSession(current.getUser(), issue(current.getUser()));
    }

    @Transactional
    public void revoke(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawRefresh)).ifPresent(token -> {
            if (token.getRevokedAt() == null) token.revoke();
        });
    }

    private String access(UserAccount user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("project-os-identity")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim("uid", user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Refresh token is invalid");
    }

    public record SessionTokens(String accessToken, String refreshToken, long accessMaxAge, long refreshMaxAge) {
    }

    public record RotatedSession(UserAccount user, SessionTokens tokens) {
    }
}
