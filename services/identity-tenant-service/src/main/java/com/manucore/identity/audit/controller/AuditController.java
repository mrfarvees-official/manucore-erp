package com.manucore.identity.audit.controller;

import com.manucore.identity.audit.Claims;
import com.manucore.identity.audit.dto.AuditDtos.AuditPage;
import com.manucore.identity.audit.service.AuditService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/audit")
@AllArgsConstructor
public class AuditController {

    private final AuditService service;

    @GetMapping
    public AuditPage list(@AuthenticationPrincipal Jwt jwt,
                          @RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "") String targetType,
                          @RequestParam(defaultValue = "") String targetId,
                          @RequestParam(defaultValue = "") String actorId,
                          @RequestParam(defaultValue = "") String action,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return service.search(Claims.tenantOf(jwt), q, targetType, targetId, actorId, action, page, size);
    }
}