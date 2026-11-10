package com.manucore.identity.account.controller;

import com.manucore.identity.account.dto.RoleDtos.CreateRoleRequest;
import com.manucore.identity.account.dto.RoleDtos.RoleResponse;
import com.manucore.identity.account.dto.RoleDtos.UpdateRoleRequest;
import com.manucore.identity.account.service.RoleService;
import com.manucore.identity.audit.ActorResolver;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identity/roles")
@AllArgsConstructor
public class RoleController {

    private static final String TENANT_CLAIM = "tenant_id";

    private final RoleService service;
    private final ActorResolver actors;

    @GetMapping
    public List<RoleResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(tenantOf(jwt));
    }

    @GetMapping("/{id}")
    public RoleResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(tenantOf(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody CreateRoleRequest request) {
        return service.create(tenantOf(jwt), actors.from(jwt), request);
    }

    @PutMapping("/{id}")
    public RoleResponse update(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID id,
                               @Valid @RequestBody UpdateRoleRequest request) {
        return service.update(tenantOf(jwt), actors.from(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(tenantOf(jwt), actors.from(jwt), id);
    }

    private static String tenantOf(Jwt jwt) {
        String tenant = jwt.getClaimAsString(TENANT_CLAIM);

        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token has no tenant");
        }

        return tenant;
    }
}