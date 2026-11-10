package com.manucore.identity.settings.dto;

import com.manucore.identity.account.Tenant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SettingsDtos {

    private static final String LOCALE = "^[a-z]{2,3}(-[A-Z]{2})?$";

    private SettingsDtos() {
    }

    public record AccountSettingsResponse(
            String name,
            String email,
            String timezone,
            String locale,
            String effectiveTimezone,
            String effectiveLocale) {
    }

    /** A blank timezone or locale means "use the company default". */
    public record UpdateAccountSettingsRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 64) String timezone,
            @Pattern(regexp = LOCALE) String locale) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record TenantSettingsResponse(
            String id, String name, String timezone, String currency, String locale) {

        public static TenantSettingsResponse from(Tenant t) {
            return new TenantSettingsResponse(
                    t.getId(), t.getName(), t.getTimezone(), t.getCurrency(), t.getLocale());
        }
    }

    public record UpdateTenantSettingsRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
            @NotBlank @Pattern(regexp = LOCALE) String locale) {
    }
}