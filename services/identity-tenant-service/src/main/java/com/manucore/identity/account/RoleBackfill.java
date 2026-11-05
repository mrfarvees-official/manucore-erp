package com.manucore.identity.account;

import com.manucore.identity.account.Tenant;
import com.manucore.identity.account.repository.TenantRepository;
import com.manucore.identity.account.repository.UserAccountRepository;
import com.manucore.identity.account.service.RoleService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Runs on every start. Idempotent: it only creates roles that are missing, so roles
 * that already exist on users (from before this table existed) keep working.
 */
@Component
@Order(2)
@AllArgsConstructor
public class RoleBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleBackfill.class);

    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final RoleService roles;

    @Override
    public void run(String... args) {
        for (Tenant tenant : tenants.findAll()) {
            String tenantId = tenant.getId();

            ensure(tenantId, RoleService.ADMIN, "Full access to everything in this company.", true);

            for (String csv : users.findRoleStrings(tenantId)) {
                for (String name : RoleService.parse(csv)) {
                    ensure(tenantId, name, null, false);
                }
            }
        }

        log.info("Role table is in sync with existing users");
    }

    private void ensure(String tenantId, String name, String description, boolean system) {
        try {
            roles.ensure(tenantId, name, description, system);
        } catch (DataIntegrityViolationException raced) {
            log.debug("Role {} was created concurrently for tenant {}", name, tenantId);
        }
    }
}