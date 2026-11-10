package com.manucore.identity.settings.controller;

import com.manucore.identity.audit.ActorResolver;
import com.manucore.identity.audit.Claims;
import com.manucore.identity.settings.dto.SettingsDtos.TenantSettingsResponse;
import com.manucore.identity.settings.dto.SettingsDtos.UpdateTenantSettingsRequest;
import com.manucore.identity.settings.service.TenantSettingsService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/tenant")
@AllArgsConstructor
public class TenantSettingsController {

    private final TenantSettingsService service;
    private final ActorResolver actors;

    @GetMapping
    public TenantSettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return TenantSettingsResponse.from(service.get(Claims.tenantOf(jwt)));
    }

    @PutMapping
    public TenantSettingsResponse update(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody UpdateTenantSettingsRequest request) {
        return TenantSettingsResponse.from(
                service.update(Claims.tenantOf(jwt), actors.from(jwt), request));
    }
}