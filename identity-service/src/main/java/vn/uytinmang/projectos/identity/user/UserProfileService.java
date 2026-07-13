package vn.uytinmang.projectos.identity.user;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.platform.api.ApiException;

@Service
class UserProfileService {
    private static final List<String> PROFILE_FIELDS = List.of(
            "phone", "department", "title", "address", "timezone", "bio");
    private final UserAccountRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final ObjectMapper mapper;

    UserProfileService(UserAccountRepository users, UserProfileRepository profiles,
                       PasswordEncoder passwords, ObjectMapper mapper) {
        this.users = users;
        this.profiles = profiles;
        this.passwords = passwords;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    JsonNode profile(UUID userId) {
        UserAccount user = user(userId);
        return profileView(user, profiles.findById(userId).orElse(null));
    }

    @Transactional
    JsonNode updateProfile(UUID userId, JsonNode body) {
        ObjectNode patch = object(body);
        UserAccount user = user(userId);
        UserProfile profile = getOrCreateProfile(userId);

        String displayName = patch.has("displayName") ? text(patch, "displayName", 100, false)
                : user.getDisplayName();
        String avatarUrl = patch.has("photoURL") ? text(patch, "photoURL", 2048, true)
                : user.getAvatarUrl();
        user.updateProfile(displayName, avatarUrl);

        for (String field : PROFILE_FIELDS) {
            if (patch.has(field)) set(profile, field, text(patch, field, field.equals("bio") ? 2000 : 255, true));
        }
        if (patch.has("skills")) profile.setSkills(skills(patch.get("skills")));
        if (patch.has("notificationPrefs")) {
            profile.setNotificationPreferences(object(patch.get("notificationPrefs")));
        }
        if (patch.has("themeName") || patch.has("themeMode")) {
            ObjectNode appearance = object(profile.getAppearancePreferences());
            if (patch.has("themeName")) appearance.put("name", text(patch, "themeName", 40, true));
            if (patch.has("themeMode")) appearance.put("mode", text(patch, "themeMode", 20, true));
            profile.setAppearancePreferences(appearance);
        }
        profile.touch();
        return profileView(user, profile);
    }

    @Transactional(readOnly = true)
    JsonNode notificationSettings(UUID userId) {
        user(userId);
        return profiles.findById(userId).map(UserProfile::getNotificationSettings)
                .orElseGet(mapper::createObjectNode);
    }

    @Transactional
    JsonNode updateNotificationSettings(UUID userId, JsonNode body) {
        ObjectNode settings = object(body);
        size(settings, 20_000);
        UserProfile profile = getOrCreateProfile(userId);
        profile.setNotificationSettings(settings);
        profile.touch();
        return settings;
    }

    @Transactional(readOnly = true)
    JsonNode appearance(UUID userId) {
        user(userId);
        return profiles.findById(userId).map(UserProfile::getAppearancePreferences)
                .orElseGet(mapper::createObjectNode);
    }

    @Transactional
    JsonNode updateAppearance(UUID userId, JsonNode body) {
        ObjectNode preferences = object(body);
        allowed(preferences, List.of("name", "mode", "theme", "fontFamily", "fontSize",
                "sidebarWidth", "contentWidth"));
        UserProfile profile = getOrCreateProfile(userId);
        profile.setAppearancePreferences(preferences);
        profile.touch();
        return preferences;
    }

    @Transactional
    void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = user(userId);
        requirePassword(user, currentPassword);
        if (passwords.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.CONFLICT, "password_unchanged", "New password must be different");
        }
        user.changePassword(passwords.encode(newPassword));
    }

    @Transactional
    void deleteAccount(UUID userId, String currentPassword) {
        UserAccount user = user(userId);
        requirePassword(user, currentPassword);
        users.delete(user);
    }

    private UserProfile getOrCreateProfile(UUID userId) {
        UserAccount user = user(userId);
        return profiles.findById(userId).orElseGet(() -> profiles.save(new UserProfile(user.getId(),
                mapper.createArrayNode(), defaultNotifications(), mapper.createObjectNode())));
    }

    private UserAccount user(UUID userId) {
        return users.findById(userId).filter(user -> user.getStatus() == UserAccount.Status.ACTIVE).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "user_missing", "User no longer exists"));
    }

    private void requirePassword(UserAccount user, String password) {
        if (user.getPasswordHash() == null || !passwords.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_password", "Current password is incorrect");
        }
    }

    private JsonNode profileView(UserAccount user, UserProfile profile) {
        ObjectNode result = mapper.createObjectNode();
        result.put("uid", user.getId().toString());
        result.put("email", user.getEmail());
        result.put("displayName", user.getDisplayName());
        if (user.getAvatarUrl() != null) result.put("photoURL", user.getAvatarUrl());
        if (profile == null) {
            result.set("skills", mapper.createArrayNode());
            result.set("notificationPrefs", defaultNotifications());
            result.put("createdAt", user.getCreatedAt().toString());
            result.put("updatedAt", user.getUpdatedAt().toString());
            return result;
        }
        put(result, "phone", profile.getPhone());
        put(result, "department", profile.getDepartment());
        put(result, "title", profile.getTitle());
        put(result, "address", profile.getAddress());
        put(result, "timezone", profile.getTimezone());
        put(result, "bio", profile.getBio());
        result.set("skills", profile.getSkills());
        result.set("notificationPrefs", profile.getNotificationPreferences());
        JsonNode appearance = profile.getAppearancePreferences();
        if (appearance.hasNonNull("name")) result.set("themeName", appearance.get("name"));
        if (appearance.hasNonNull("mode")) result.set("themeMode", appearance.get("mode"));
        result.put("createdAt", profile.getCreatedAt().toString());
        result.put("updatedAt", profile.getUpdatedAt().toString());
        return result;
    }

    private ObjectNode defaultNotifications() {
        ObjectNode result = mapper.createObjectNode();
        result.put("email", true);
        result.put("desktop", true);
        result.put("slack", false);
        return result;
    }

    private ObjectNode object(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_body", "Expected a JSON object");
        }
        return ((ObjectNode) body).deepCopy();
    }

    private JsonNode skills(JsonNode skills) {
        if (!skills.isArray() || skills.size() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_skills", "skills must be an array of at most 50 items");
        }
        for (JsonNode skill : skills) {
            if (!skill.isTextual() || skill.asText().isBlank() || skill.asText().length() > 80) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_skills", "Each skill must be 1-80 characters");
            }
        }
        return skills.deepCopy();
    }

    private String text(ObjectNode body, String field, int max, boolean nullable) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return nullable ? null : invalid(field);
        if (!value.isTextual()) return invalid(field);
        String result = value.asText().trim();
        if ((!nullable && result.isEmpty()) || result.length() > max) return invalid(field);
        return result.isEmpty() ? null : result;
    }

    private String invalid(String field) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_profile", "Invalid field: " + field);
    }

    private void set(UserProfile profile, String field, String value) {
        switch (field) {
            case "phone" -> profile.setPhone(value);
            case "department" -> profile.setDepartment(value);
            case "title" -> profile.setTitle(value);
            case "address" -> profile.setAddress(value);
            case "timezone" -> profile.setTimezone(value);
            case "bio" -> profile.setBio(value);
            default -> throw new IllegalArgumentException("Unknown profile field");
        }
    }

    private void allowed(ObjectNode body, List<String> fields) {
        body.properties().forEach(field -> {
            if (!fields.contains(field.getKey())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_preference", "Unknown preference: " + field.getKey());
            }
            if (!field.getValue().isTextual() || field.getValue().asText().length() > 40) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_preference", "Invalid preference: " + field.getKey());
            }
        });
    }

    private void size(JsonNode body, int max) {
        if (body.toString().length() > max) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "preferences_too_large", "Preferences are too large");
        }
    }

    private void put(ObjectNode target, String field, String value) {
        if (value != null) target.put(field, value);
    }
}
