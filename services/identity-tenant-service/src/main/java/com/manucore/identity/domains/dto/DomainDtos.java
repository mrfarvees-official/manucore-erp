package com.manucore.identity.domains.dto;

import com.manucore.identity.domains.TenantDomain;
import com.manucore.identity.domains.service.DomainService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DomainDtos {

    private DomainDtos() {
    }

    public record DomainResponse(
            UUID id,
            String domain,
            String type,
            String status,
            boolean primary,
            String verificationRecordName,
            String verificationRecordValue,
            Instant verifiedAt,
            Instant createdAt,
            Instant lastCheckedAt,
            String lastCheckResult) {

        public static DomainResponse from(TenantDomain d) {
            boolean pendingCustom =
                    d.getStatus() == DomainStatus.PENDING && d.getType() == DomainType.CUSTOM;

            return new DomainResponse(
                    d.getId(),
                    d.getDomain(),
                    d.getType().name(),
                    d.getStatus().name(),
                    d.isPrimaryDomain(),
                    pendingCustom ? DomainService.VERIFY_PREFIX + d.getDomain() : null,
                    pendingCustom ? d.getVerificationToken() : null,
                    d.getVerifiedAt(),
                    d.getCreatedAt(),
                    d.getLastCheckedAt(),
                    d.getLastCheckResult());
        }
    }

    public record AddDomainRequest(@NotBlank @Size(max = 253) String domain) {
    }

    public record UpdateDomainRequest(@NotNull Boolean primary) {
    }

    public record TenantLookupResponse(String tenantId, String tenantName, String domain) {
    }
}