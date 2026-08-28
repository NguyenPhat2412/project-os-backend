package com.projectos.backend.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "company_assets")
public class CompanyAsset {
    public enum Status { AVAILABLE, IN_USE, MAINTENANCE, RETIRED }

    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 50) private String category;
    @Column(name = "serial_number", length = 150) private String serialNumber;
    @Column(length = 150) private String model;
    @Column(length = 150) private String manufacturer;
    @Column(name = "purchase_date") private LocalDate purchaseDate;
    @Column(name = "purchase_price", precision = 19, scale = 2) private BigDecimal purchasePrice;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "warranty_until") private LocalDate warrantyUntil;
    @Column(length = 200) private String location;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 200) private String supplier;
    private String notes;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CompanyAsset() {}

    public CompanyAsset(UUID organizationId, String code, String name, String category, Status status, UUID actor) {
        this.organizationId = organizationId;
        this.code = code;
        this.name = name;
        this.category = category;
        this.status = status == null ? Status.AVAILABLE : status;
        this.currency = "VND";
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    @PrePersist void created() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }
    @PreUpdate void updated() { updatedAt = Instant.now(); }

    public void update(String code, String name, String category, String serialNumber, String model, String manufacturer,
                       LocalDate purchaseDate, BigDecimal purchasePrice, String currency, LocalDate warrantyUntil,
                       String location, Status status, String supplier, String notes, UUID actor) {
        if (code != null) this.code = code;
        if (name != null) this.name = name;
        if (category != null) this.category = category;
        if (serialNumber != null) this.serialNumber = serialNumber;
        if (model != null) this.model = model;
        if (manufacturer != null) this.manufacturer = manufacturer;
        if (purchaseDate != null) this.purchaseDate = purchaseDate;
        if (purchasePrice != null) this.purchasePrice = purchasePrice;
        if (currency != null) this.currency = currency;
        if (warrantyUntil != null) this.warrantyUntil = warrantyUntil;
        if (location != null) this.location = location;
        if (status != null) this.status = status;
        if (supplier != null) this.supplier = supplier;
        if (notes != null) this.notes = notes;
        this.updatedBy = actor;
    }
    public void markDeleted(UUID actor) { this.deleted = true; this.status = Status.RETIRED; this.updatedBy = actor; }
    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getSerialNumber() { return serialNumber; }
    public String getModel() { return model; }
    public String getManufacturer() { return manufacturer; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public String getCurrency() { return currency; }
    public LocalDate getWarrantyUntil() { return warrantyUntil; }
    public String getLocation() { return location; }
    public Status getStatus() { return status; }
    public String getSupplier() { return supplier; }
    public String getNotes() { return notes; }
    public boolean isDeleted() { return deleted; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
