package com.manucore.policy;

import com.manucore.policy.dto.PolicyDtos;
import com.manucore.policy.enums.Effect;
import com.manucore.policy.enums.PolicyPriority;
import com.manucore.policy.enums.SubjectType;
import com.manucore.policy.repository.ActionRepository;
import com.manucore.policy.repository.ResourceRepository;
import com.manucore.policy.service.PolicyAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Seed the action/resource catalog and default policies. Idempotent.
 *
 * To register a new service's resources, add them to RESOURCES below
 * (convention: SERVICE.COLLECTION, e.g. PROCUREMENT.ORDERS) and redeploy.
 * Administrators then get the access through  then "*" policy and can write
 * narrower policies against the new names.
 */
@Component
@ConditionalOnProperty(
        name = "manucore.authorization.seed.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DefaultPolicySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultPolicySeeder.class);
    static final String SYSTEM_ACTOR = "system";
    static final List<String> ACTIONS =
            List.of("*", "READ", "CREATE", "UPDATE", "DELETE", "ADJUST");
    static final List<String> RESOURCES =
            List.of("*",
                    "INVENTORY.ITEMS",
                    "INVENTORY.ADJUSTMENTS",
                    "INVENTORY.PING",
                    "INVENTORY.POLICIES",
                    "INVENTORY.CATALOG",
                    "IDENTITY.USERS",
                    "IDENTITY.ROLES");

    private final ActionRepository actions;
    private final ResourceRepository resources;
    private final PolicyAdminService admin;
    private final List<String> tenants;

    public DefaultPolicySeeder(
            ActionRepository actions,
            ResourceRepository resources,
            PolicyAdminService admin,
            @Value("${manucore.authorization.seed.tenants:acme}") String tenants) {
        this.actions = actions;
        this.resources = resources;
        this.admin = admin;
        this.tenants = Arrays.stream(tenants.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        seedCatalog();
        tenants.forEach(this::seedTenant);
    }

    private void seedCatalog() {
        for (String name : ACTIONS) {
            if (actions.findByNameIgnoreCase(name).isEmpty()){
                try{
                    Action action = new Action();
                    action.setName(name);
                    actions.save(action);
                } catch (DataIntegrityViolationException raced) {
                    log.debug("Action {} was created concurrently", name);
                }
            }
        }

        for (String name: RESOURCES) {
            if (resources.findByNameIgnoreCase(name).isEmpty()) {
                try {
                    Resource resource = new Resource();
                    resource.setName(name);
                    resources.save(resource);
                } catch (DataIntegrityViolationException raced) {
                    log.debug("Resource {} was created concurrently", name);
                }
            }
        }
    }

    private void seedTenant(String tenantId) {
        for (PolicyDtos.PolicyRequest policy : defaults()) {
            try {
                if (!admin.exists(tenantId, policy.name())) {
                    admin.create(tenantId, SYSTEM_ACTOR, policy);
                    log.info("Seeded policy '{}' for tenant {}", policy.name(), tenantId);
                }
            } catch (DataIntegrityViolationException | ResponseStatusException raced) {
                log.debug("Policy '{}' already exists for tenant {}", policy.name(), tenantId);
            }
        }
    }

    private List<PolicyDtos.PolicyRequest> defaults() {
        List<PolicyDtos.PolicyRequest> list = new ArrayList<>();

        // HIGH priority so CRITICAL policy can restrict administrators
        list.add(allow(
                "Tenant Administrators: full access",
                "Administrators may perform every action on every resource.",
                "*",
                "*",
                PolicyPriority.HIGH,
                "ADMIN"));

        for (String action : List.of("READ", "CREATE", "UPDATE", "DELETE")) {
            list.add(allow(
                    "Administrators: " + action.toLowerCase() + " policies",
                    "Administrators may " + action.toLowerCase() + " authorization policies.",
                    action,
                    "AUTHORIZATION.POLICIES",
                    PolicyPriority.HIGH,
                    "ADMIN"));
        }

        list.add(allow(
                "Administrators: read catalog",
                "Administrators may read the action and resource catalog.",
                "READ",
                "AUTHORIZATION.CATALOG",
                PolicyPriority.HIGH,
                "ADMIN"));

        for (String action : List.of("READ", "CREATE", "UPDATE", "ADJUST")) {
            list.add(allow(
                    "Inventory managers: " + action.toLowerCase() + " items",
                    "Inventory managers may call the inventory ping endpoint.",
                    "READ",
                    "INVENTORY.PING",
                    PolicyPriority.MEDIUM,
                    "INVENTORY_MANAGER"));
        }

        list.add(allow(
                "Inventory managers: read inventory ping",
                "Inventory managers may call the inventory ping endpoint.",
                "READ",
                "INVENTORY.PING",
                PolicyPriority.MEDIUM,
                "INVENTORY_MANAGER"));

        return list;
    }

    private static PolicyDtos.PolicyRequest allow(
            String name,
            String description,
            String action,
            String resource,
            PolicyPriority priority,
            String role) {
        return new PolicyDtos.PolicyRequest(
                name,
                description,
                action,
                resource,
                Effect.ALLOW,
                priority,
                List.of(new PolicyDtos.SubjectDto(SubjectType.ROLE, role)),
                List.of());
    }
}
