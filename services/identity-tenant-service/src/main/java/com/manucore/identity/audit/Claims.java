package com.manucore.identity.audit;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public final class Claims {

    private Claims() {}

    public static String tenantOf(Jwt jwt) {
        String tenant = jwt.getClaimAsString("tenant_id");

        if (tenant == null || tenant.isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token has no tenant");

        return tenant;
    }
}
