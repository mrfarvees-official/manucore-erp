package com.manucore.identity.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Mirrors the authorization service's /permissions response. */
public record PermissionMatrix(
        String tenantId,
        String userId,
        List<String> roles,
        boolean canManagePolicies,
        Instant generatedAt,
        List<String> actions,
        List<String> resources,
        Map<String, Map<String, Boolean>> matrix,
        List<String> allowed
) {
}