package vn.uytinmang.projectos.identity.user;

import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.platform.api.ApiException;
import vn.uytinmang.projectos.platform.api.PageResponse;

@Service
class UserDirectoryService {
    private final UserAccountRepository users;

    UserDirectoryService(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    PageResponse<DirectoryUser> list(int page, int size, String search) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_pagination", "page must be >= 0 and size 1-100");
        }
        Specification<UserAccount> specification = (root, query, builder) ->
                builder.equal(root.get("status"), UserAccount.Status.ACTIVE);
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("email")), pattern),
                    builder.like(builder.lower(root.get("displayName")), pattern)));
        }
        var result = users.findAll(specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayName")));
        return PageResponse.of(result.getContent().stream().map(DirectoryUser::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    record DirectoryUser(UUID id, String email, String displayName, String avatarUrl) {
        static DirectoryUser from(UserAccount user) {
            return new DirectoryUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
        }
    }
}
