package com.projectos.backend.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organizations")
public class Organization {
    public enum Status { ACTIVE, DISABLED }

    @Id private UUID id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String timezone;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "code") private String code;
    @Column(name = "name_vi") private String nameVi;
    @Column(name = "name_en") private String nameEn;
    @Column(name = "short_name") private String shortName;
    @Column(name = "tax_code") private String taxCode;
    @Column(name = "legal_representative") private String legalRepresentative;
    @Column(name = "representative_title") private String representativeTitle;
    @Column(name = "headquarters_address") private String headquartersAddress;
    @Column(name = "hotline") private String hotline;
    @Column(name = "email") private String email;
    @Column(name = "website") private String website;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "details", columnDefinition = "jsonb") private String details;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Organization() {}
    public Organization(String name, String slug, String timezone, UUID createdBy) {
        this.name = name; this.slug = slug; this.timezone = timezone; this.createdBy = createdBy; this.status = Status.ACTIVE;
        this.nameVi = name; this.shortName = slug;
    }
    @PrePersist void created() { if (id == null) id = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void update(String name, String slug, String timezone, Status status,
                       String code, String nameVi, String nameEn, String shortName, String taxCode,
                       String legalRepresentative, String representativeTitle, String headquartersAddress,
                       String hotline, String email, String website, JsonNode details) {
        if (name != null) this.name = name;
        if (slug != null) this.slug = slug;
        if (timezone != null) this.timezone = timezone;
        if (status != null) this.status = status;
        if (code != null) this.code = code;
        if (nameVi != null) this.nameVi = nameVi;
        if (nameEn != null) this.nameEn = nameEn;
        if (shortName != null) this.shortName = shortName;
        if (taxCode != null) this.taxCode = taxCode;
        if (legalRepresentative != null) this.legalRepresentative = legalRepresentative;
        if (representativeTitle != null) this.representativeTitle = representativeTitle;
        if (headquartersAddress != null) this.headquartersAddress = headquartersAddress;
        if (hotline != null) this.hotline = hotline;
        if (email != null) this.email = email;
        if (website != null) this.website = website;
        if (details != null) this.details = details.toString();
    }
    public UUID getId() { return id; } public String getName() { return name; } public String getSlug() { return slug; }
    public String getTimezone() { return timezone; } public Status getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public String getCode() { return code; } public String getNameVi() { return nameVi; } public String getNameEn() { return nameEn; }
    public String getShortName() { return shortName; } public String getTaxCode() { return taxCode; }
    public String getLegalRepresentative() { return legalRepresentative; } public String getRepresentativeTitle() { return representativeTitle; }
    public String getHeadquartersAddress() { return headquartersAddress; } public String getHotline() { return hotline; }
    public String getEmail() { return email; } public String getWebsite() { return website; }
    public String getDetailsJson() { return details; }
    public JsonNode getDetails() {
        if (details == null || details.isBlank()) return null;
        try { return new ObjectMapper().readTree(details); } catch (RuntimeException exception) { return null; }
    }
}
