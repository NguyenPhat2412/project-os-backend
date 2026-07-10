package vn.uytinmang.projectos.config;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.uytinmang.projectos.user.UserAccount;
import vn.uytinmang.projectos.user.UserAccountRepository;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String displayName;

    public AdminBootstrap(
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.email}") String email,
            @Value("${app.bootstrap-admin.password}") String password,
            @Value("${app.bootstrap-admin.display-name}") String displayName) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (email.isBlank() || password.isBlank()) return;
        if (password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("BOOTSTRAP_ADMIN_PASSWORD must contain 8 to 72 characters");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(normalizedEmail).ifPresentOrElse(user -> user.setRole(UserAccount.Role.ADMIN),
                () -> users.save(new UserAccount(normalizedEmail, passwordEncoder.encode(password),
                        displayName.trim(), UserAccount.Role.ADMIN)));
    }
}
