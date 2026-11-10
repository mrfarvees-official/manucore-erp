package com.manucore.identity.domains;

import com.manucore.identity.account.Tenant;
import com.manucore.identity.account.repository.TenantRepository;
import com.manucore.identity.domains.dto.DomainStatus;
import com.manucore.identity.domains.dto.DomainType;
import com.manucore.identity.domains.repository.TenantDomainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Demo only: gives a company with no domains a verified <tenant-id>.<base domain>. */
@Component
@Order(3)
@ConditionalOnProperty(name = "manucore.identity.seed-demo-data", havingValue = "true")
public class DemoDomainSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDomainSeeder.class);
    private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    private final TenantDomainRepository domains;
    private final TenantRepository tenants;
    private final List<String> bases;

    public DemoDomainSeeder(TenantDomainRepository domains,
                            TenantRepository tenants,
                            @Value("${manucore.identity.base-domains:localhost}") String baseDomains) {
        this.domains = domains;
        this.tenants = tenants;
        this.bases = Arrays.stream(baseDomains.split(","))
                .map(b -> b.trim().toLowerCase(Locale.ROOT))
                .filter(b -> !b.isEmpty())
                .toList();
    }

    @Override
    public void run(String... args) {
        for (Tenant tenant : tenants.findAll()) {
            String label = tenant.getId().toLowerCase(Locale.ROOT);

            if (!LABEL.matcher(label).matches() || domains.countByTenantId(tenant.getId()) > 0) {
                continue;
            }

            boolean first = true;

            for (String base : bases) {
                String domain = label + "." + base;

                if (domains.existsByDomainIgnoreCase(domain)) {
                    continue;
                }

                TenantDomain seeded = new TenantDomain(
                        tenant.getId(), domain, DomainType.SUBDOMAIN, DomainStatus.VERIFIED, null);
                seeded.setPrimaryDomain(first);
                first = false;

                domains.save(seeded);
                log.info("Demo domain {} created for tenant {}", domain, tenant.getId());
            }
        }
    }
}