package vn.uytinmang.projectos.identity.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.identity.auth.TokenService;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.PageResponse;

@Service
class AdminUserService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    AdminUserService(UserAccountRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    PageResponse<UserView> list(int page, int size, String search, String role, String status) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "page must be >= 0 and size 1-100");
        }
        Specification<UserAccount> specification = Specification.unrestricted();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("email")), pattern),
                    builder.like(builder.lower(root.get("displayName")), pattern)));
        }
        if (role != null && !role.isBlank()) {
            UserAccount.Role value = role(role);
            specification = specification.and((root, query, builder) -> builder.equal(root.get("role"), value));
        }
        if (status != null && !status.isBlank()) {
            UserAccount.Status value = status(status);
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), value));
        }
        var result = users.findAll(specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result.getContent().stream().map(UserView::from).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    UserView get(UUID id) {
        return UserView.from(user(id));
    }

    @Transactional
    UserView create(AdminUserController.CreateRequest request) {
        String email = email(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "email_exists", "Email is already registered");
        }
        if (request.password() != null && request.passwordHash() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_password_source",
                    "Provide password or passwordHash, not both");
        }
        String passwordHash = request.passwordHash();
        if (passwordHash != null && !passwordHash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_password_hash", "passwordHash must be bcrypt");
        }
        if (passwordHash == null && request.password() != null) passwordHash = passwords.encode(request.password());
        UserAccount user = users.save(new UserAccount(email, passwordHash,
                request.displayName().trim(), role(request.role())));
        return UserView.from(user);
    }

    @Transactional
    UserView update(UUID id, UUID actorId, AdminUserController.UpdateRequest request) {
        UserAccount user = user(id);
        String nextEmail = request.email() == null ? user.getEmail() : email(request.email());
        users.findByEmail(nextEmail).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "email_exists", "Email is already registered");
        });
        String nextName = request.displayName() == null ? user.getDisplayName() : request.displayName().trim();
        if (nextName.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_name", "displayName is required");
        UserAccount.Role nextRole = request.role() == null ? user.getRole() : role(request.role());
        UserAccount.Status nextStatus = request.status() == null ? user.getStatus() : status(request.status());
        boolean authorizationChanged = nextRole != user.getRole() || nextStatus != user.getStatus();
        if (id.equals(actorId) && (nextRole != UserAccount.Role.ROOT_ADMIN
                || nextStatus != UserAccount.Status.ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "cannot_disable_self", "You cannot remove your own root access");
        }
        String avatar = request.avatarUrl() == null ? user.getAvatarUrl() : blankToNull(request.avatarUrl());
        user.updateAdminFields(nextEmail, nextName, avatar, nextRole, nextStatus);
        if (request.password() != null) user.changePassword(passwords.encode(request.password()));
        if (authorizationChanged) tokens.revokeAll(id);
        return UserView.from(user);
    }

    @Transactional
    void disable(UUID id, UUID actorId) {
        if (id.equals(actorId)) {
            throw new ApiException(HttpStatus.CONFLICT, "cannot_disable_self", "You cannot disable your own account");
        }
        UserAccount user = user(id);
        user.setStatus(UserAccount.Status.DISABLED);
        tokens.revokeAll(id);
    }

    private UserAccount user(UUID id) {
        return users.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "user_not_found", "User not found"));
    }

    private String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private String blankToNull(String value) { return value.isBlank() ? null : value.trim(); }

    private UserAccount.Role role(String value) {
        try {
            return UserAccount.Role.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_role", "role must be ROOT_ADMIN or USER");
        }
    }

    private UserAccount.Status status(String value) {
        try {
            return UserAccount.Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_status", "status must be ACTIVE or DISABLED");
        }
    }

    record UserView(UUID id, String email, String displayName, String avatarUrl, String role, String status,
                    Instant createdAt, Instant updatedAt) {
        static UserView from(UserAccount user) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
                    user.getRole().name(), user.getStatus().name(), user.getCreatedAt(), user.getUpdatedAt());
        }
    }
}
