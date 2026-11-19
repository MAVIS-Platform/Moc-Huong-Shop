package com.mochuong.identity.config;

import com.mochuong.identity.domain.UserAccount;
import com.mochuong.identity.domain.UserRole;
import com.mochuong.identity.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds lab demo accounts on startup only when the users table is empty.
 */
@Component
public class IdentityDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityDataSeeder.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public IdentityDataSeeder(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.password:MocHuong@123}") String demoPassword) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userAccountRepository.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(demoPassword);
        Instant now = Instant.now();
        List<UserAccount> demoUsers = List.of(
                new UserAccount(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "admin@mochuong.vn", hash, "Quan Tri Vien Moc Huong", UserRole.ADMIN, now),
                new UserAccount(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "staff@mochuong.vn", hash, "Nhan Vien Kho Tram", UserRole.STAFF, now),
                new UserAccount(UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "customer@mochuong.vn", hash, "Khach Hang Yeu Tram", UserRole.CUSTOMER, now)
        );
        userAccountRepository.saveAll(demoUsers);
        log.info("Seeded {} demo user accounts for Moc Huong Shop lab", demoUsers.size());
    }
}
