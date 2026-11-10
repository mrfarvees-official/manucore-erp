package com.manucore.identity.domains.service;

import com.manucore.identity.account.Tenant;
import com.manucore.identity.account.repository.TenantRepository;
import com.manucore.identity.audit.dto.Actor;
import com.manucore.identity.audit.service.AuditService;
import com.manucore.identity.audit.ChangeSet;
import com.manucore.identity.domains.DnsTxtLookup;
import com.manucore.identity.domains.TenantDomain;
import com.manucore.identity.domains.dto.DomainDtos.AddDomainRequest;
import com.manucore.identity.domains.dto.DomainDtos.TenantLookupResponse;
import com.manucore.identity.domains.dto.DomainStatus;
import com.manucore.identity.domains.dto.DomainType;
import com.manucore.identity.domains.repository.TenantDomainRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DomainService {

    public static final String VERIFY_PREFIX = "_manucore-verify.";

    private static final int MAX_DOMAINS = 10;
    private static final Pattern HOST =
            Pattern.compile("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Set<String> RESERVED = Set.of(
            "www", "api", "admin", "app", "auth", "login", "mail", "static",
            "assets", "gateway", "status", "support");

    private final TenantDomainRepository domains;
    private final TenantRepository tenants;
    private final AuditService audit;
    private final DnsTxtLookup dns;
    private final List<String> baseDomains;
    private final boolean strictLogin;

    public DomainService(TenantDomainRepository domains,
                         TenantRepository tenants,
                         AuditService audit,
                         DnsTxtLookup dns,
                         @Value("${manucore.identity.base-domains:localhost}") String baseDomains,
                         @Value("${manucore.identity.domain-login:off}") String domainLogin) {
        this.domains = domains;
        this.tenants = tenants;
        this.audit = audit;
        this.dns = dns;
        this.baseDomains = Arrays.stream(baseDomains.split(","))
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .toList();
        this.strictLogin = "strict".equalsIgnoreCase(domainLogin.trim());
    }

    public boolean isStrictLogin() {
        return strictLogin;
    }

    @Transactional(readOnly = true)
    public List<TenantDomain> list(String tenantId) {
        return domains.findByTenantIdOrderByPrimaryDomainDescDomainAsc(tenantId);
    }

    @Transactional
    public TenantDomain add(String tenantId, Actor actor, AddDomainRequest request) {
        String domain = clean(request.domain());

        if (!HOST.matcher(domain).matches()) {
            throw bad("Enter a valid domain name, for example erp.example.com");
        }

        if (domains.countByTenantId(tenantId) >= MAX_DOMAINS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At most " + MAX_DOMAINS + " domains");
        }

        if (domains.existsByDomainIgnoreCase(domain)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Domain already in use: " + domain);
        }

        Optional<String> base = baseDomainOf(domain);
        boolean subdomain = base.isPresent();

        if (subdomain) {
            String label = domain.substring(0, domain.length() - base.get().length() - 1);

            if (label.contains(".")) {
                throw bad("Use a single name in front of " + base.get() + ", for example acme." + base.get());
            }

            if (RESERVED.contains(label)) {
                throw bad("The name \"" + label + "\" is reserved");
            }
        } else if (baseDomains.contains(domain)) {
            throw bad("That is the platform's own domain");
        }

        TenantDomain created = new TenantDomain(
                tenantId,
                domain,
                subdomain ? DomainType.SUBDOMAIN : DomainType.CUSTOM,
                subdomain ? DomainStatus.VERIFIED : DomainStatus.PENDING,
                subdomain ? null : newToken());

        if (subdomain && !hasPrimary(tenantId)) {
            created.setPrimaryDomain(true);
        }

        try {
            domains.saveAndFlush(created);
        } catch (DataIntegrityViolationException raced) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Domain already in use: " + domain);
        }

        audit.record(tenantId, actor, "DOMAIN_ADDED", "DOMAIN", created.getId().toString(), domain,
                new ChangeSet()
                        .add("domain", null, domain)
                        .add("type", null, created.getType())
                        .add("status", null, created.getStatus())
                        .asList());

        return created;
    }

    /** Checks the DNS TXT record. A failed check is returned with its reason, not thrown. */
    @Transactional
    public TenantDomain verify(String tenantId, Actor actor, UUID id) {
        TenantDomain domain = find(tenantId, id);

        if (domain.getStatus() == DomainStatus.VERIFIED) {
            return domain;
        }

        String recordName = VERIFY_PREFIX + domain.getDomain();
        String result;
        boolean verified = false;

        try {
            List<String> values = dns.txt(recordName);
            verified = values.stream().anyMatch(v -> v.equals(domain.getVerificationToken()));

            if (verified) {
                result = "Verified";
            } else if (values.isEmpty()) {
                result = "No TXT record found at " + recordName;
            } else {
                result = "A TXT record exists at " + recordName + " but its value does not match";
            }
        } catch (Exception failure) {
            result = "DNS lookup failed (" + failure.getClass().getSimpleName() + ")";
        }

        domain.recordCheck(result);

        if (verified) {
            domain.markVerified();

            if (!hasPrimary(tenantId)) {
                domain.setPrimaryDomain(true);
            }

            audit.record(tenantId, actor, "DOMAIN_VERIFIED", "DOMAIN", domain.getId().toString(),
                    domain.getDomain(),
                    new ChangeSet()
                            .add("status", DomainStatus.PENDING, DomainStatus.VERIFIED)
                            .asList());
        }

        return domain;
    }

    @Transactional
    public TenantDomain makePrimary(String tenantId, Actor actor, UUID id) {
        TenantDomain domain = find(tenantId, id);

        if (domain.getStatus() != DomainStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only verified domains can be primary");
        }

        if (domain.isPrimaryDomain()) {
            return domain;
        }

        String previous = list(tenantId).stream()
                .filter(TenantDomain::isPrimaryDomain)
                .peek(d -> d.setPrimaryDomain(false))
                .map(TenantDomain::getDomain)
                .findFirst()
                .orElse(null);

        domain.setPrimaryDomain(true);

        audit.record(tenantId, actor, "DOMAIN_PRIMARY_CHANGED", "DOMAIN", domain.getId().toString(),
                domain.getDomain(),
                new ChangeSet().add("primary domain", previous, domain.getDomain()).asList());

        return domain;
    }

    @Transactional
    public void remove(String tenantId, Actor actor, UUID id) {
        TenantDomain domain = find(tenantId, id);
        boolean wasPrimary = domain.isPrimaryDomain();

        domains.delete(domain);

        ChangeSet changes = new ChangeSet()
                .add("domain", domain.getDomain(), null)
                .add("status", domain.getStatus(), null);

        if (wasPrimary) {
            Optional<TenantDomain> next = list(tenantId).stream()
                    .filter(d -> !d.getId().equals(id))
                    .filter(d -> d.getStatus() == DomainStatus.VERIFIED)
                    .min(Comparator.comparing(TenantDomain::getCreatedAt));

            next.ifPresent(d -> {
                d.setPrimaryDomain(true);
                changes.add("primary domain", domain.getDomain(), d.getDomain());
            });
        }

        audit.record(tenantId, actor, "DOMAIN_REMOVED", "DOMAIN", id.toString(),
                domain.getDomain(), changes.asList());
    }

    /** Which company does this host belong to? Used by the login page and by login. */
    @Transactional(readOnly = true)
    public Optional<TenantLookupResponse> resolve(String hostHeader) {
        String host = hostOnly(hostHeader);

        if (host == null) {
            return Optional.empty();
        }

        return domains.findByDomainIgnoreCase(host)
                .filter(d -> d.getStatus() == DomainStatus.VERIFIED)
                .flatMap(d -> tenants.findById(d.getTenantId())
                        .filter(Tenant::isActive)
                        .map(t -> new TenantLookupResponse(t.getId(), t.getName(), d.getDomain())));
    }

    public Optional<String> resolveTenantId(String hostHeader) {
        return resolve(hostHeader).map(TenantLookupResponse::tenantId);
    }

    private TenantDomain find(String tenantId, UUID id) {
        return domains.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found"));
    }

    private boolean hasPrimary(String tenantId) {
        return list(tenantId).stream().anyMatch(TenantDomain::isPrimaryDomain);
    }

    private Optional<String> baseDomainOf(String domain) {
        return baseDomains.stream().filter(base -> domain.endsWith("." + base)).findFirst();
    }

    /* Admin input: only a domain name, no scheme, path or port. */
    private static String clean(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);

        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.contains("/") || value.contains(":") || value.contains(" ")) {
            throw bad("Enter only the domain name, without http://, a port or a path");
        }

        return value;
    }

    /* A Host header: drop the port and a trailing dot. */
    private static String hostOnly(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank() || hostHeader.startsWith("[")) {
            return null;
        }

        String host = hostHeader.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');

        if (colon >= 0) {
            host = host.substring(0, colon);
        }

        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }

        return host.isEmpty() || host.length() > 253 ? null : host;
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);

        return "mc-verify-" + HexFormat.of().formatHex(bytes);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}