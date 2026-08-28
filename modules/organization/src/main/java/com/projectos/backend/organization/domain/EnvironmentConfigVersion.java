package com.projectos.backend.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "environment_config_versions")
public class EnvironmentConfigVersion {
    @Id private UUID id;
    @Column(name = "config_path", nullable = false) private String configPath;
    @Column(nullable = false, length = 128) private String checksum;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "changed_keys", nullable = false, columnDefinition = "jsonb")
    private String changedKeys;
    @Column(name = "snapshot_path", nullable = false) private String snapshotPath;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "reload_required", nullable = false) private boolean reloadRequired;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(columnDefinition = "text") private String notes;

    protected EnvironmentConfigVersion() { }

    public EnvironmentConfigVersion(String configPath, String checksum, String changedKeys, String snapshotPath,
                                    String status, boolean reloadRequired, UUID createdBy, String notes) {
        this.id = UUID.randomUUID();
        this.configPath = configPath;
        this.checksum = checksum;
        this.changedKeys = changedKeys;
        this.snapshotPath = snapshotPath;
        this.status = status;
        this.reloadRequired = reloadRequired;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public String getSnapshotPath() { return snapshotPath; }
    public String getChecksum() { return checksum; }
    public String getChangedKeys() { return changedKeys; }
    public String getStatus() { return status; }
    public boolean isReloadRequired() { return reloadRequired; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getNotes() { return notes; }

    public void markRolledBack() {
        this.status = "ROLLED_BACK";
    }
}
