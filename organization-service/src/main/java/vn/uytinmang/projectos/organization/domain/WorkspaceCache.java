package vn.uytinmang.projectos.organization.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class WorkspaceCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;
    private final Duration ttl;

    WorkspaceCache(StringRedisTemplate redis, ObjectMapper json,
                   @Value("${app.workspace-cache.enabled:true}") boolean enabled,
                   @Value("${app.workspace-cache.ttl-seconds:30}") long ttlSeconds) {
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    }

    Optional<OrganizationApplicationService.Workspace> get(UUID organizationId, UUID actorId) {
        if (!enabled) return Optional.empty();
        try {
            String value = redis.opsForValue().get(key(organizationId, actorId));
            return value == null ? Optional.empty()
                    : Optional.of(json.readValue(value, OrganizationApplicationService.Workspace.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    void put(UUID organizationId, UUID actorId, OrganizationApplicationService.Workspace workspace) {
        if (!enabled) return;
        try {
            redis.opsForValue().set(key(organizationId, actorId), json.writeValueAsString(workspace), ttl);
        } catch (Exception ignored) {
            // Cache failure must never break authorization data loaded from PostgreSQL.
        }
    }

    void invalidateOrganization(UUID organizationId) {
        increment("projectos:workspace:org-version:" + organizationId);
    }

    void invalidateSubject(UUID actorId) {
        if (actorId != null) increment("projectos:workspace:subject-version:" + actorId);
    }

    private String key(UUID organizationId, UUID actorId) {
        return "projectos:workspace:v1:" + organizationId + ':' + version("org-version:" + organizationId)
                + ':' + actorId + ':' + version("subject-version:" + actorId);
    }

    private String version(String suffix) {
        if (!enabled) return "0";
        try {
            String value = redis.opsForValue().get("projectos:workspace:" + suffix);
            return value == null ? "0" : value;
        } catch (Exception ignored) {
            return "0";
        }
    }

    private void increment(String key) {
        if (!enabled) return;
        try {
            redis.opsForValue().increment(key);
        } catch (Exception ignored) {
            // Redis is an optimization only.
        }
    }
}
