package vn.uytinmang.projectos.identity.config;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.identity.user.UserAccount;
import vn.uytinmang.projectos.identity.user.UserAccountRepository;

@Component
class AdminBootstrap implements CommandLineRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final String displayName;

    AdminBootstrap(UserAccountRepository users, PasswordEncoder passwords,
                   @Value("${app.bootstrap-admin.email:}") String email,
                   @Value("${app.bootstrap-admin.password:}") String password,
                   @Value("${app.bootstrap-admin.display-name:Administrator}") String displayName) {
        this.users = users;
        this.passwords = passwords;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (email.isBlank() || password.isBlank()) return;
        if (password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Bootstrap password must contain 8 to 72 characters");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(normalized).ifPresentOrElse(user -> user.setRole(UserAccount.Role.ROOT_ADMIN),
                () -> users.save(new UserAccount(normalized, passwords.encode(password), displayName.trim(),
                        UserAccount.Role.ROOT_ADMIN)));
    }
}
