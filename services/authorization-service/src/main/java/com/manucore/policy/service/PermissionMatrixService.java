package com.manucore.policy.service;

import com.manucore.policy.Action;
import com.manucore.policy.Policy;
import com.manucore.policy.Resource;
import com.manucore.policy.dto.CallerContext;
import com.manucore.policy.dto.PermissionMatrix;
import com.manucore.policy.repository.ActionRepository;
import com.manucore.policy.repository.PolicyRepository;
import com.manucore.policy.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PermissionMatrixService {

    public static final String WILDCARD = "*";
    private final PolicyRepository policies;
    private final ActionRepository actions;
    private final ResourceRepository resources;
    private final PolicyEngine engine;

    /* One transaction, so lazily loaded condition trees stay readable by the engine. */
    @Transactional(readOnly = true)
    public PermissionMatrix compute(CallerContext caller)  {
        List<Policy> tenantPolicies = policies.findAllForTenant(caller.tenantId());

        List<String> actionNames = actions.findAll().stream()
                .map(Action::getName)
                .filter(name -> !WILDCARD.equals(name))
                .sorted()
                .toList();

        List<String> resourceNames = resources.findAll().stream()
                .map(Resource::getName)
                .filter(name -> !WILDCARD.equals(name))
                .sorted()
                .toList();

        Map<String, Map<String, Boolean>> matrix = new TreeMap<>();
        List<String> allowed = new ArrayList<>();

        for (String resource : resourceNames) {
            Map<String, Boolean> row = new TreeMap<>();

            for (String action : actionNames) {
                boolean ok = engine.decide(
                        tenantPolicies,
                        caller.tenantId(),
                        caller.userId(),
                        caller.roles(),
                        caller.departmentId(),
                        caller.serviceId(),
                        action,
                        resource,
                        caller.attributesWithMethod(ActionMethods.methodFor(action))
                ).allowed();

                row.put(action, ok);
                if (ok) allowed.add(resource + ":" + action);
            }

            matrix.put(resource, row);
        }

        boolean canManagePolicies = caller.hasRole(PolicyAdminGuard.ADMIN_ROLE)
                || Boolean.TRUE.equals(
                        matrix.getOrDefault(PolicyAdminGuard.POLICIES_RESOURCE, Map.of())
                                .get("CREATE"));

        return new PermissionMatrix(
                caller.tenantId(),
                caller.userId(),
                caller.roles(),
                canManagePolicies,
                Instant.now(),
                actionNames,
                resourceNames,
                matrix,
                allowed);
    }
}
