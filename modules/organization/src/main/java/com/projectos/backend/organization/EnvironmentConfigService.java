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

    private static final Set<String> ALLOWED_KEYS = EnvironmentConfigCatalog.ALLOWED_KEYS;

    static boolean isAllowedKey(String key) {
        return ALLOWED_KEYS.contains(key);
    }

    private static final Set<String> SECRET_KEYS = EnvironmentConfigCatalog.SECRET_KEYS;

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
        Map<String, String> current = currentValues();
        for (String key : ALLOWED_KEYS) {
            String value = current.get(key);
            if (value == null || value.isBlank()) continue;
            result.put(key, SECRET_KEYS.contains(key) ? MASKED_VALUE : value);
        }
        return result;
    }

    Map<String, String> currentValues() {
        Map<String, String> values = new LinkedHashMap<>(source);
        if (file == null || !Files.isRegularFile(file)) return values;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) continue;
                int separator = trimmed.indexOf('=');
                String key = trimmed.substring(0, separator).trim();
                if (!ALLOWED_KEYS.contains(key)) continue;
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
            return values;
        } catch (IOException exception) {
            return source;
        }
    }

    public boolean isFileConfigured() {
        return file != null;
    }

    public String configuredFilePath() {
        return file == null ? null : file.toAbsolutePath().toString();
    }

    public byte[] currentFileBytes() {
        if (file == null || !Files.exists(file)) return new byte[0];
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new com.projectos.backend.platform.api.ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "environment_file_unreadable", "Không thể đọc cấu hình hệ thống.");
        }
    }

    public void update(Map<String, String> updates) {
        if (file == null) {
            throw new com.projectos.backend.platform.api.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "environment_file_not_configured", "Cấu hình hệ thống chưa sẵn sàng để cập nhật.");
        }
        Map<String, String> normalizedUpdates = EnvironmentConfigValidation.validate(updates);

        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            List<String> existingLines = Files.exists(file)
                    ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                    : new ArrayList<>();
            Map<String, String> effectiveUpdates = new LinkedHashMap<>();
            normalizedUpdates.forEach((key, value) -> {
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
