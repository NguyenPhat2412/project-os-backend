package com.projectos.backend.organization;

import com.projectos.backend.organization.domain.EnvironmentConfigVersion;
import com.projectos.backend.organization.domain.EnvironmentConfigVersionRepository;
import com.projectos.backend.platform.api.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentConfigVersionService {
    private final EnvironmentConfigService environmentConfig;
    private final EnvironmentConfigVersionRepository versions;
    private final Path snapshotDirectory;
    private final boolean readOnly;

    public EnvironmentConfigVersionService(EnvironmentConfigService environmentConfig,
                                           EnvironmentConfigVersionRepository versions,
                                           @Value("${app.environment-snapshot-directory:${java.io.tmpdir}/project-os-environment-snapshots}") String snapshotDirectory) {
        this(environmentConfig, versions, snapshotDirectory, false);
    }

    @Autowired
    public EnvironmentConfigVersionService(EnvironmentConfigService environmentConfig,
                                           EnvironmentConfigVersionRepository versions,
                                           @Value("${app.environment-snapshot-directory:${java.io.tmpdir}/project-os-environment-snapshots}") String snapshotDirectory,
                                           @Value("${app.environment-config.read-only:false}") boolean readOnly) {
        this.environmentConfig = environmentConfig;
        this.versions = versions;
        this.snapshotDirectory = Path.of(snapshotDirectory);
        this.readOnly = readOnly;
    }

    public Map<String, String> snapshot(boolean root) {
        requireRoot(root);
        return new LinkedHashMap<>(environmentConfig.snapshot());
    }

    public boolean isFileConfigured() {
        return environmentConfig.isFileConfigured();
    }

    @Transactional
    public EnvironmentConfigVersion apply(boolean root, UUID actor, Map<String, String> updates) {
        requireRoot(root);
        requireWritable();
        if (actor == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Phiên làm việc không hợp lệ.");
        if (!environmentConfig.isFileConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "environment_file_not_configured",
                    "Cấu hình hệ thống chưa sẵn sàng để cập nhật.");
        }
        Map<String, String> normalized = EnvironmentConfigValidation.validate(updates);
        byte[] before = environmentConfig.currentFileBytes();
        Path snapshot = createSnapshot(before);
        try {
            environmentConfig.update(normalized);
            String checksum = checksum(before);
            EnvironmentConfigVersion version = new EnvironmentConfigVersion(
                    environmentConfig.configuredFilePath(), checksum, jsonArray(normalized.keySet()), snapshot.toString(),
                    "APPLIED", true, actor, "Cấu hình được cập nhật từ trang quản trị.");
            return versions.save(version);
        } catch (RuntimeException exception) {
            restore(snapshot);
            throw exception;
        }
    }

    public List<EnvironmentConfigVersion> list(boolean root) {
        requireRoot(root);
        return versions.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional
    public EnvironmentConfigVersion rollback(boolean root, UUID actor, UUID versionId) {
        requireRoot(root);
        requireWritable();
        if (actor == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Phiên làm việc không hợp lệ.");
        EnvironmentConfigVersion version = versions.findById(versionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "environment_version_not_found", "Không tìm thấy phiên bản cấu hình.") );
        if (!environmentConfig.isFileConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "environment_file_not_configured",
                    "Cấu hình hệ thống chưa sẵn sàng để khôi phục.");
        }
        Path snapshot = Path.of(version.getSnapshotPath());
        if (!Files.isRegularFile(snapshot)) {
            throw new ApiException(HttpStatus.GONE, "environment_snapshot_unavailable", "Phiên bản cấu hình không còn khả dụng.");
        }
        byte[] before = environmentConfig.currentFileBytes();
        Path currentSnapshot = createSnapshot(before);
        try {
            restore(snapshot);
            version.markRolledBack();
            return versions.save(version);
        } catch (RuntimeException exception) {
            restore(currentSnapshot);
            throw exception;
        }
    }

    private Path createSnapshot(byte[] bytes) {
        try {
            Files.createDirectories(snapshotDirectory);
            Path target = snapshotDirectory.resolve("environment-" + UUID.randomUUID() + ".snapshot");
            Files.write(target, bytes);
            restrict(target);
            return target;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "environment_snapshot_failed",
                    "Không thể tạo bản sao cấu hình an toàn.");
        }
    }

    private void restore(Path snapshot) {
        try {
            Path target = Path.of(environmentConfig.configuredFilePath());
            Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".project-os-env-rollback-", ".tmp");
            Files.copy(snapshot, temporary, StandardCopyOption.REPLACE_EXISTING);
            restrict(temporary);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            restrict(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "environment_rollback_failed",
                    "Không thể khôi phục cấu hình hệ thống.");
        }
    }

    private static String jsonArray(Set<String> keys) {
        return keys.stream().map(key -> "\"" + key + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void restrict(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) { }
    }

    private static void requireRoot(boolean root) {
        if (!root) throw new ApiException(HttpStatus.FORBIDDEN, "root_admin_required", "Chỉ quản trị cấp cao mới được quản lý cấu hình này.");
    }

    private void requireWritable() {
        if (readOnly) throw new ApiException(HttpStatus.FORBIDDEN, "environment_config_read_only",
                "Cấu hình production chỉ được xem; hãy thay đổi qua secret manager hoặc release pipeline.");
    }
}
