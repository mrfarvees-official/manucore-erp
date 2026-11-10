package com.manucore.identity.account.dto;

import java.util.List;

public record MeResponse(
        String id,
        String email,
        String name,
        String tenantId,
        String tenantName,
        String timezone,
        String locale,
        String currency,
        List<String> roles,
        /** false when the authorization service could not be reached; hide privileged UI. */
        boolean permissionsAvailable,
        PermissionMatrix permissions
) {
}