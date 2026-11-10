package com.manucore.identity.account.controller;

import com.manucore.identity.account.AuthorizationClient;
import com.manucore.identity.account.Tenant;
import com.manucore.identity.account.service.TokenService;
import com.manucore.identity.account.UserAccount;
import com.manucore.identity.account.dto.LoginRequest;
import com.manucore.identity.account.dto.LoginResponse;
import com.manucore.identity.account.dto.MeResponse;
import com.manucore.identity.account.dto.PermissionMatrix;
import com.manucore.identity.account.repository.TenantRepository;
import com.manucore.identity.account.repository.UserAccountRepository;
import com.manucore.identity.domains.service.DomainService;
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JWKSet jwkSet;
    private final AuthorizationClient authorization;
    private final DomainService domains;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(request.email())
                .filter(UserAccount::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        tenants.findById(user.getTenantId())
                .filter(Tenant::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        String host = request.host();

        if (host == null || host.isBlank()) {
            if (domains.isStrictLogin()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
        } else {
            Optional<String> hostTenant = domains.resolveTenantId(host);
            boolean wrongCompany = hostTenant.isPresent()
                    ? !hostTenant.get().equals(user.getTenantId())
                    : domains.isStrictLogin();

            if (wrongCompany) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
        }

        return tokenService.issue(user);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();

        UserAccount user = users.findById(UUID.fromString(userId))
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        String tenantName = tenants.findById(user.getTenantId())
                .map(Tenant::getName)
                .orElse(user.getTenantId());

        List<String> roles = Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();

        Optional<PermissionMatrix> permissions =
                authorization.fetchPermissions(jwt.getTokenValue());

        Tenant tenant = tenants.findById(user.getTenantId()).orElse(null);

        return new MeResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getName(),
                user.getTenantId(),
                tenant != null ? tenant.getName() : user.getTenantId(),
                user.getTimezone() != null ? user.getTimezone() : (tenant != null ? tenant.getTimezone() : "UTC"),
                user.getLocale() != null ? user.getLocale() : (tenant != null ? tenant.getLocale() : "en-US"),
                tenant != null ? tenant.getCurrency() : "USD",
                roles,
                permissions.isPresent(),
                permissions.orElse(null));
    }

    /* Public keys only. The gateway uses this to verify token signatures. */
    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return jwkSet.toJSONObject(true);
    }
}