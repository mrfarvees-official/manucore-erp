package com.manucore.identity.settings.controller;

import com.manucore.identity.audit.ActorResolver;
import com.manucore.identity.settings.dto.SettingsDtos.AccountSettingsResponse;
import com.manucore.identity.settings.dto.SettingsDtos.ChangePasswordRequest;
import com.manucore.identity.settings.dto.SettingsDtos.UpdateAccountSettingsRequest;
import com.manucore.identity.settings.service.AccountSettingsService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/* Own-account settings. Under /api/v1/auth, the gateway only requires a valid token. */
@RestController
@RequestMapping("/api/v1/auth/me")
@AllArgsConstructor
public class AccountSettingsController {

    private final AccountSettingsService service;
    private final ActorResolver actors;

    @GetMapping("/settings")
    public AccountSettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/settings")
    public AccountSettingsResponse update(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody UpdateAccountSettingsRequest request) {
        return service.update(actors.from(jwt), UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(actors.from(jwt), UUID.fromString(jwt.getSubject()), request);
    }
}