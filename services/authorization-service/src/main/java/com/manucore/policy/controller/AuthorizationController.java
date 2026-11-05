package com.manucore.policy.controller;

import com.manucore.policy.dto.AuthorizationDecision;
import com.manucore.policy.dto.AuthorizationRequest;
import com.manucore.policy.dto.CallerContext;
import com.manucore.policy.dto.PermissionMatrix;
import com.manucore.policy.service.CallerContextFactory;
import com.manucore.policy.service.PermissionMatrixService;
import com.manucore.policy.service.PolicyEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authorization")
@RequiredArgsConstructor
public class AuthorizationController {

    private final PolicyEngine policyEngine;
    private final PermissionMatrixService matrixService;
    private final CallerContextFactory callers;

    /* Decision for the caller. Identity always comes from the JWT, never from the body. */
    @SuppressWarnings("NullableProblems")
    @PostMapping("/check")
    public ResponseEntity<AuthorizationDecision> checkAuthorization(
            @Valid @RequestBody AuthorizationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http
    ) {
        CallerContext caller = callers.from(jwt, http);

        return ResponseEntity.ok(policyEngine.authorize(
                caller.tenantId(),
                caller.userId(),
                caller.roles(),
                caller.departmentId(),
                caller.serviceId(),
                request.action(),
                request.resource(),
                caller.attributesWithMethod(callers.requestMethod(http))));
    }

    /* The caller's permission matrix, for conditional rendering in the UI. */
    @GetMapping("/permissions")
    public PermissionMatrix permissions(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http
    ) {
        return matrixService.compute(callers.from(jwt, http));
    }
}
