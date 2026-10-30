package com.manucore.policy;

import com.manucore.policy.dto.AuthorizationDecision;
import com.manucore.policy.dto.AuthorizationRequest;
import com.manucore.policy.enums.ConditionAttribute;
import com.manucore.policy.service.PolicyEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/authorization")
@RequiredArgsConstructor
public class AuthorizationController {

    private final PolicyEngine policyEngine;

    @PostMapping("/check")
    public ResponseEntity<AuthorizationDecision> checkAuthorization(
            @Valid @RequestBody AuthorizationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest
    ) {
        // Extract trusted identity information from JWT
        String tenantId = jwt.getClaimAsString("tenant_id");
        String userId = jwt.getSubject();

        if (tenantId == null ||
                tenantId.isBlank() ||
                userId == null ||
                userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Required identity claims are missing.");
        }

        // Extract roles from JWT
        List<String> roles = jwt.getClaimAsStringList("roles");

        if (roles == null) roles = List.of();

        // Optional identity claims
        String departmentId = jwt.getClaimAsString("department_id");
        String serviceId = jwt.getClaimAsString("service_id");

        // Build trusted request context
        Map<ConditionAttribute, Object> attributes = new EnumMap<>(ConditionAttribute.class);

        attributes.put(ConditionAttribute.IP_ADDRESS, httpRequest.getRemoteAddr());
        attributes.put(ConditionAttribute.REQUEST_METHOD, httpRequest.getMethod());
        attributes.put(ConditionAttribute.TIME, LocalTime.now());
        attributes.put(ConditionAttribute.DAY_OF_WEEK, DayOfWeek.from(LocalDate.now()).name());

        // Execute the policy engine
        AuthorizationDecision decision = policyEngine
                .authorize(
                        tenantId,
                        userId,
                        roles,
                        departmentId,
                        serviceId,
                        request.action(),
                        request.resource(),
                        attributes
                );

        return ResponseEntity.ok(decision);
    }
}
