package vn.uytinmang.projectos.identity.user;

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

@Entity
@Table(name = "users")
public class UserAccount {
    public enum Role { ROOT_ADMIN, USER }
    public enum Status { ACTIVE, DISABLED }

    @Id private UUID id;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash") private String passwordHash;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "avatar_url") private String avatarUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, String displayName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.status = Status.ACTIVE;
    }

    @PrePersist void create() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setRole(Role role) { this.role = role; }
    public void setStatus(Status status) { this.status = status; }
    public void updateAdminFields(String email, String displayName, String avatarUrl, Role role, Status status) {
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.role = role;
        this.status = status;
    }
    public void updateProfile(String displayName, String avatarUrl) {
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }
    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; }
}
