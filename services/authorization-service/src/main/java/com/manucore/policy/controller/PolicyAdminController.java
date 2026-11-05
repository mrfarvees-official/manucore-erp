package com.manucore.policy.controller;

import com.manucore.policy.dto.CallerContext;
import com.manucore.policy.dto.PolicyDtos;
import com.manucore.policy.service.CallerContextFactory;
import com.manucore.policy.service.PolicyAdminGuard;
import com.manucore.policy.service.PolicyAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/authorization/policies")
@RequiredArgsConstructor
public class PolicyAdminController {

    private static final String RESOURCE = PolicyAdminGuard.POLICIES_RESOURCE;

    private final PolicyAdminService service;
    private final PolicyAdminGuard guard;
    private final CallerContextFactory callers;

    @GetMapping
    public PolicyDtos.PolicyPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http) {

        CallerContext caller = callers.from(jwt, http);
        guard.require(caller, RESOURCE, "READ");

        return service.list(
                caller.tenantId(),
                Math.max(0, page),
                Math.min(Math.max(1, size), 100));
    }

    @GetMapping("/{id}")
    public PolicyDtos.PolicyResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http) {
        CallerContext caller = callers.from(jwt, http);
        guard.require(caller, RESOURCE, "READ");

        return service.get(caller.tenantId(), id);
    }

    @SuppressWarnings("NullableProblems")
    @PostMapping
    public ResponseEntity<PolicyDtos.PolicyResponse> create(
            @Valid @RequestBody PolicyDtos.PolicyRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http) {
        CallerContext caller = callers.from(jwt, http);
        guard.require(caller, RESOURCE, "CREATE");

        PolicyDtos.PolicyResponse created = service.create(caller.tenantId(), caller.userId(), request);

        return ResponseEntity
                .created(URI.create("/api/v1/authorization/policies/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public PolicyDtos.PolicyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody PolicyDtos.PolicyRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http) {
        CallerContext caller = callers.from(jwt, http);
        guard.require(caller, RESOURCE, "UPDATE");

        return service.update(caller.tenantId(), id, caller.userId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http) {
        CallerContext caller = callers.from(jwt, http);
        guard.require(caller, RESOURCE, "DELETE");

        service.delete(caller.tenantId(), id);
    }
}
