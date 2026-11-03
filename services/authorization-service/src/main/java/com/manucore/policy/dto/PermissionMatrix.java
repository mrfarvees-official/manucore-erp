package com.manucore.policy.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the caller may do right now. For UI rendering only: the gateway
 * enforces the same rules on every request.
 */
public record PermissionMatrix(
        String tenantId,
        String userId,
        List<String> roles,
        boolean canManagePolicies,
        Instant generatedAt,
        List<String> actions,
        List<String> resources,
        Map<String, Map<String, Boolean>> matrix,   // resource -> action -> allowed
        List<String> allowed                        // flat "RESOURCE:ACTION" list
) {
}