package com.projectos.backend.organization;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Owns the small, explicit environment configuration surface exposed to the
 * administrator UI. Secrets are never returned in clear text.
 */
@Service
public final class EnvironmentConfigService {
    public static final String MASKED_VALUE = "••••••••";

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "PROJECT_OS_API_PUBLIC_URL", "PROJECT_OS_API_INTERNAL_URL", "GATEWAY_PORT",
            "CORS_ALLOWED_ORIGINS", "POSTGRES_HOST", "POSTGRES_PORT", "POSTGRES_DB",
            "POSTGRES_USER", "POSTGRES_PASSWORD", "REDIS_HOST", "REDIS_PORT",
            "NEXT_PUBLIC_WS_URL", "WS_PORT", "S3_BUCKET", "S3_REGION", "S3_ACCESS_KEY",
            "S3_SECRET_KEY", "S3_ENDPOINT", "MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD",
            "JWT_SECRET", "INTERNAL_SERVICE_TOKEN", "BOOTSTRAP_ADMIN_EMAIL",
            "BOOTSTRAP_ADMIN_PASSWORD", "BOOTSTRAP_ADMIN_NAME", "ANTHROPIC_API_KEY",
            "GEMINI_API_KEY", "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
            "GOOGLE_OAUTH_REDIRECT_URI", "GOOGLE_OAUTH_SUCCESS_URL");

    private static final Set<String> SECRET_KEYS = Set.of(
            "POSTGRES_PASSWORD", "S3_SECRET_KEY", "MINIO_ROOT_PASSWORD", "JWT_SECRET",
            "INTERNAL_SERVICE_TOKEN", "BOOTSTRAP_ADMIN_PASSWORD", "ANTHROPIC_API_KEY",
            "GEMINI_API_KEY", "GOOGLE_CLIENT_SECRET");

    private final Path file;
    private final Map<String, String> source;

    @Autowired
    public EnvironmentConfigService(@Value("${app.environment-file:}") String configuredFile, Environment environment) {
        this(configuredFile.isBlank() ? null : Path.of(configuredFile), source(environment));
    }

    public EnvironmentConfigService(Map<String, String> source) {
        this(null, source);
    }

    public EnvironmentConfigService(Path file, Map<String, String> source) {
        this.file = file;
        this.source = Map.copyOf(source);
    }

    public Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : ALLOWED_KEYS) {
            String value = source.get(key);
            if (value == null || value.isBlank()) continue;
            result.put(key, SECRET_KEYS.contains(key) ? MASKED_VALUE : value);
        }
        return result;
    }

    public boolean isFileConfigured() {
        return file != null;
    }

    public void update(Map<String, String> updates) {
        if (file == null) throw new IllegalStateException("Environment file is not configured");
        updates.keySet().forEach(this::requireAllowed);

        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> existingLines = Files.exists(file)
                    ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                    : new ArrayList<>();
            Map<String, String> effectiveUpdates = new LinkedHashMap<>();
            updates.forEach((key, value) -> {
                if (value != null && !value.isBlank() && !MASKED_VALUE.equals(value)) effectiveUpdates.put(key, value);
            });
            Set<String> writtenKeys = new java.util.HashSet<>();
            List<String> outputLines = new ArrayList<>();
            for (String line : existingLines) {
                if (line.isBlank() || line.trim().startsWith("#") || !line.contains("=")) {
                    outputLines.add(line);
                    continue;
                }
                int separator = line.indexOf('=');
                String key = line.substring(0, separator).trim();
                if (effectiveUpdates.containsKey(key)) {
                    outputLines.add(key + "=" + effectiveUpdates.get(key));
                    writtenKeys.add(key);
                } else {
                    outputLines.add(line);
                }
            }
            effectiveUpdates.forEach((key, value) -> {
                if (!writtenKeys.contains(key)) outputLines.add(key + "=" + value);
            });

            Path temporary = Files.createTempFile(parent, ".project-os-env-", ".tmp");
            String content = String.join("\n", outputLines) + (outputLines.isEmpty() ? "" : "\n");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            restrictPermissions(temporary);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            restrictPermissions(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Environment configuration could not be saved", exception);
        }
    }

    private void requireAllowed(String key) {
        if (!ALLOWED_KEYS.contains(key)) throw new IllegalArgumentException("Environment key is not allowed");
    }

    private static Map<String, String> source(Environment environment) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : ALLOWED_KEYS) {
            String value = environment.getProperty(key);
            if (value != null) values.put(key, value);
        }
        return values;
    }

    private void restrictPermissions(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows development environments do not expose POSIX permissions.
        }
    }
}
