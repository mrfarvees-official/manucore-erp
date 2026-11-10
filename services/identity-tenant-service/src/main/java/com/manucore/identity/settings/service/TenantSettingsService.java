package com.manucore.identity.settings.service;

import com.manucore.identity.account.Tenant;
import com.manucore.identity.account.repository.TenantRepository;
import com.manucore.identity.audit.dto.Actor;
import com.manucore.identity.audit.service.AuditService;
import com.manucore.identity.audit.ChangeSet;
import com.manucore.identity.settings.dto.SettingsDtos.UpdateTenantSettingsRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@AllArgsConstructor
public class TenantSettingsService {

    private final TenantRepository tenants;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Tenant get(String tenantId) {
        return find(tenantId);
    }

    @Transactional
    public Tenant update(String tenantId, Actor actor, UpdateTenantSettingsRequest request) {
        Tenant tenant = find(tenantId);

        String name = request.name().trim();
        String timezone = SettingsValidation.timezone(request.timezone());
        String currency = SettingsValidation.currency(request.currency());
        String locale = request.locale().trim();

        ChangeSet changes = new ChangeSet()
                .add("name", tenant.getName(), name)
                .add("timezone", tenant.getTimezone(), timezone)
                .add("currency", tenant.getCurrency(), currency)
                .add("locale", tenant.getLocale(), locale);

        if (changes.isEmpty()) {
            return tenant;
        }

        tenant.setName(name);
        tenant.setTimezone(timezone);
        tenant.setCurrency(currency);
        tenant.setLocale(locale);

        audit.record(tenantId, actor, "TENANT_SETTINGS_UPDATED", "TENANT", tenantId, name, changes.asList());

        return tenant;
    }

    private Tenant find(String tenantId) {
        return tenants.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}