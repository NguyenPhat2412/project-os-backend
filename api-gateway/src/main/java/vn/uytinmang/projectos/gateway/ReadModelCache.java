package vn.uytinmang.projectos.gateway;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class ReadModelCache {
    static final String HEADER = "X-ProjectOS-Cache";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    ReadModelCache(StringRedisTemplate redis, ObjectMapper json,
                   @Value("${app.read-model.cache-ttl-seconds:30}") long ttlSeconds) {
        this.redis = redis;
        this.json = json;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    }

    String key(UUID projectId, String subjectId, String scope) {
        return "projectos:rm:v1:" + projectId + ':' + projectVersion(projectId) + ':'
                + subjectId + ':' + subjectVersion(subjectId) + ':' + scope;
    }

    <T> Optional<T> get(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, type));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    boolean put(String key, Object value) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    void invalidateProject(UUID projectId) {
        increment("projectos:rm:project-version:" + projectId);
    }

    void invalidateSubject(String subjectId) {
        increment("projectos:rm:subject-version:" + subjectId);
    }

    private String projectVersion(UUID projectId) {
        return value("projectos:rm:project-version:" + projectId);
    }

    private String subjectVersion(String subjectId) {
        return value("projectos:rm:subject-version:" + subjectId);
    }

    private String value(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? "0" : value;
        } catch (Exception ignored) {
            return "0";
        }
    }

    private void increment(String key) {
        try {
            redis.opsForValue().increment(key);
        } catch (Exception ignored) {
            // Cache is an optimization; mutations must still succeed when Redis is unavailable.
        }
    }
}
