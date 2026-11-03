package com.manucore.identity.auth;

import java.util.List;

public record MeResponse(
        String id,
        String email,
        String name,
        String tenantId,
        List<String> roles,
        /** false when the authorization service could not be reached; hide privileged UI. */
        boolean permissionsAvailable,
        PermissionMatrix permissions
) {
}