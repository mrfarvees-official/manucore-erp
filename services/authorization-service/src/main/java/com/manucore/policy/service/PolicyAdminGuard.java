package com.manucore.policy.service;

import com.manucore.policy.dto.CallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Protects this service's own management endpoints.
 *
 * ADMIN is a break-glass role: it is always allowed, so a bad policy can never
 * lock administrators out of policy management. Other roles can be delegated
 * access through ordinary policies on the AUTHORIZATION.* resources.
 */

@Component
@RequiredArgsConstructor
public class PolicyAdminGuard {

    public static final String ADMIN_ROLE = "ADMIN";
    public static final String POLICIES_RESOURCE = "AUTHORIZATION.POLICIES";
    public static final String CATALOG_RESOURCE = "AUTHORIZATION.CATALOG";

    private final PolicyEngine engine;

    public boolean isAllowed(CallerContext caller, String resource, String action) {
        if (caller.hasRole(ADMIN_ROLE)) {
            return true;
        }

        return engine.authorize(
                caller.tenantId(),
                caller.userId(),
                caller.roles(),
                caller.departmentId(),
                caller.serviceId(),
                action,
                resource,
                caller.attributesWithMethod(ActionMethods.methodFor(action))
        ).allowed();
    }

    public void require(CallerContext caller, String resource, String action) {
        if (!isAllowed(caller, resource, action))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not permitted to perform this operation.");
    }
}
