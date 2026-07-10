package vn.uytinmang.projectos.identity.user;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_profiles")
class UserProfile {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    private String phone;
    private String department;
    private String title;
    private String address;
    private String timezone;
    private String bio;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode skills;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", nullable = false, columnDefinition = "jsonb")
    private JsonNode notificationPreferences;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_settings", nullable = false, columnDefinition = "jsonb")
    private JsonNode notificationSettings;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appearance_preferences", nullable = false, columnDefinition = "jsonb")
    private JsonNode appearancePreferences;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    UserProfile(UUID userId, JsonNode emptyArray, JsonNode notificationPreferences, JsonNode emptyObject) {
        this.userId = userId;
        this.skills = emptyArray;
        this.notificationPreferences = notificationPreferences;
        this.notificationSettings = emptyObject.deepCopy();
        this.appearancePreferences = emptyObject.deepCopy();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void touch() { updatedAt = Instant.now(); }

    UUID getUserId() { return userId; }
    String getPhone() { return phone; }
    String getDepartment() { return department; }
    String getTitle() { return title; }
    String getAddress() { return address; }
    String getTimezone() { return timezone; }
    String getBio() { return bio; }
    JsonNode getSkills() { return skills; }
    JsonNode getNotificationPreferences() { return notificationPreferences; }
    JsonNode getNotificationSettings() { return notificationSettings; }
    JsonNode getAppearancePreferences() { return appearancePreferences; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }

    void setPhone(String phone) { this.phone = phone; }
    void setDepartment(String department) { this.department = department; }
    void setTitle(String title) { this.title = title; }
    void setAddress(String address) { this.address = address; }
    void setTimezone(String timezone) { this.timezone = timezone; }
    void setBio(String bio) { this.bio = bio; }
    void setSkills(JsonNode skills) { this.skills = skills; }
    void setNotificationPreferences(JsonNode preferences) { this.notificationPreferences = preferences; }
    void setNotificationSettings(JsonNode settings) { this.notificationSettings = settings; }
    void setAppearancePreferences(JsonNode preferences) { this.appearancePreferences = preferences; }
}
