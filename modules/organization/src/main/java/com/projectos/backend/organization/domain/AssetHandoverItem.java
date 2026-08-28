package com.projectos.backend.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_handover_items")
public class AssetHandoverItem {
    @Id private UUID id;
    @Column(name = "handover_id", nullable = false) private UUID handoverId;
    @Column(name = "asset_id", nullable = false) private UUID assetId;
    @Column(name = "condition_out", length = 500) private String conditionOut;
    @Column(name = "condition_in", length = 500) private String conditionIn;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(name = "returned_at") private Instant returnedAt;
    private String note;
    protected AssetHandoverItem() {}
    public AssetHandoverItem(UUID handoverId, UUID assetId, String conditionOut, String note) {
        this.handoverId = handoverId; this.assetId = assetId; this.conditionOut = conditionOut; this.note = note;
    }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); }
    public void issue() { issuedAt = Instant.now(); }
    public void returnItem(String conditionIn) { this.conditionIn = conditionIn; returnedAt = Instant.now(); }
    public UUID getId() { return id; } public UUID getHandoverId() { return handoverId; } public UUID getAssetId() { return assetId; }
    public String getConditionOut() { return conditionOut; } public String getConditionIn() { return conditionIn; }
    public Instant getIssuedAt() { return issuedAt; } public Instant getReturnedAt() { return returnedAt; } public String getNote() { return note; }
}
