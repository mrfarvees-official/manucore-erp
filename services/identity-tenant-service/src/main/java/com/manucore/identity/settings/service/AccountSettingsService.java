package com.manucore.identity.settings.service;

import com.manucore.identity.account.Tenant;
import com.manucore.identity.account.UserAccount;
import com.manucore.identity.account.repository.TenantRepository;
import com.manucore.identity.account.repository.UserAccountRepository;
import com.manucore.identity.audit.dto.Actor;
import com.manucore.identity.audit.service.AuditService;
import com.manucore.identity.audit.ChangeSet;
import com.manucore.identity.settings.dto.SettingsDtos.AccountSettingsResponse;
import com.manucore.identity.settings.dto.SettingsDtos.ChangePasswordRequest;
import com.manucore.identity.settings.dto.SettingsDtos.UpdateAccountSettingsRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@AllArgsConstructor
public class AccountSettingsService {

    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public AccountSettingsResponse get(UUID userId) {
        UserAccount user = find(userId);

        return response(user, tenants.findById(user.getTenantId()).orElse(null));
    }

    @Transactional
    public AccountSettingsResponse update(Actor actor, UUID userId, UpdateAccountSettingsRequest request) {
        UserAccount user = find(userId);

        String name = request.name().trim();
        String timezone = blank(request.timezone()) ? null : SettingsValidation.timezone(request.timezone());
        String locale = blank(request.locale()) ? null : request.locale().trim();

        ChangeSet changes = new ChangeSet()
                .add("name", user.getName(), name)
                .add("timezone", user.getTimezone(), timezone)
                .add("locale", user.getLocale(), locale);

        if (!changes.isEmpty()) {
            user.setName(name);
            user.setTimezone(timezone);
            user.setLocale(locale);

            audit.record(user.getTenantId(), actor, "ACCOUNT_SETTINGS_UPDATED", "USER",
                    user.getId().toString(), user.getEmail(), changes.asList());
        }

        return response(user, tenants.findById(user.getTenantId()).orElse(null));
    }

    @Transactional
    public void changePassword(Actor actor, UUID userId, ChangePasswordRequest request) {
        UserAccount user = find(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The new password must be different");
        }

        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        audit.record(user.getTenantId(), actor, "PASSWORD_CHANGED", "USER",
                user.getId().toString(), user.getEmail(),
                new ChangeSet().note("password", "changed by the user").asList());
    }

    private AccountSettingsResponse response(UserAccount user, Tenant tenant) {
        String tenantTimezone = tenant == null ? "UTC" : tenant.getTimezone();
        String tenantLocale = tenant == null ? "en-US" : tenant.getLocale();

        return new AccountSettingsResponse(
                user.getName(),
                user.getEmail(),
                user.getTimezone(),
                user.getLocale(),
                user.getTimezone() != null ? user.getTimezone() : tenantTimezone,
                user.getLocale() != null ? user.getLocale() : tenantLocale);
    }

    private UserAccount find(UUID userId) {
        return users.findById(userId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}