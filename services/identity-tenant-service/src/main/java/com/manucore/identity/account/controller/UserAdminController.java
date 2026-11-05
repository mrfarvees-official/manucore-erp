package com.manucore.identity.account.controller;

import com.manucore.identity.account.dto.RoleDtos;
import com.manucore.identity.account.dto.UserDtos.CreateUserRequest;
import com.manucore.identity.account.dto.UserDtos.UpdateUserRequest;
import com.manucore.identity.account.dto.UserDtos.UserPage;
import com.manucore.identity.account.dto.UserDtos.UserResponse;
import com.manucore.identity.account.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identity/users")
@AllArgsConstructor
public class UserAdminController {

    private static final String TENANT_CLAIM = "tenant_id";

    private final UserAdminService service;

    @GetMapping
    public UserPage list(@AuthenticationPrincipal Jwt jwt,
                         @RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "ALL") String status,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return UserPage.from(service.search(tenantOf(jwt), q, status, page, size));
    }

    /* Role options for the user form's checkboxes. Needs only IDENTITY.USERS:READ. */
    @GetMapping("/roles")
    public List<RoleDtos.RoleOption> roles(@AuthenticationPrincipal Jwt jwt) {
        return service.roleOptions(tenantOf(jwt));
    }

    @GetMapping("/{id}")
    public UserResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return UserResponse.from(service.get(tenantOf(jwt), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(service.create(tenantOf(jwt), request));
    }

    @PutMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(
                service.update(tenantOf(jwt), UUID.fromString(jwt.getSubject()), id, request));
    }

    private static String tenantOf(Jwt jwt) {
        String tenant = jwt.getClaimAsString(TENANT_CLAIM);

        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token has no tenant");
        }

        return tenant;
    }
}