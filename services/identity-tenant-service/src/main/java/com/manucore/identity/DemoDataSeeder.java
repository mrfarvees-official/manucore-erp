package com.manucore.identity;

import com.manucore.identity.domain.Tenant;
import com.manucore.identity.domain.UserAccount;
import com.manucore.identity.domain.repository.TenantRepository;
import com.manucore.identity.domain.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "manucore.identity.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(TenantRepository tenants,
                          UserAccountRepository users,
                          PasswordEncoder passwordEncoder,
                          @Value("${manucore.identity.demo-password:Admin@123") String demoPassword) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) {
        if (!tenants.existsById("acme")) {
            tenants.save(
                    new Tenant("acme", "Acme Manufacturing", true)
            );
        }

        if (!users.existsByTenantIdAndEmailIgnoreCase("acme", "admin@acme.com")) {
            users.save(
                    new UserAccount(
                            UUID.randomUUID(),
                            "acme",
                            "admin@acme.com",
                            passwordEncoder.encode(demoPassword),
                            "ADMIN,INVENTORY_MANAGER",
                            true
                    )
            );
        }
    }
}
