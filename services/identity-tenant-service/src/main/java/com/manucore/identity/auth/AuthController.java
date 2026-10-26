package com.manucore.identity.auth;

import com.manucore.identity.domain.Tenant;
import com.manucore.identity.domain.UserAccount;
import com.manucore.identity.domain.repository.TenantRepository;
import com.manucore.identity.domain.repository.UserAccountRepository;
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JWKSet jwkSet;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = users.findByTenantAndEmailIgnoreCase(request.tenantId(), request.email())
                .filter(UserAccount::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        tenants.findById(user.getTenantId())
                .filter(Tenant::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        return tokenService.issue(user);
    }

    /* Public keys only. The gateway uses this to verify token signatures. */
    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return jwkSet.toJSONObject(true);
    }
}
