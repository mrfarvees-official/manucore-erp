package com.manucore.identity.domains;

import com.manucore.identity.domains.dto.DomainStatus;
import com.manucore.identity.domains.dto.DomainType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_domains", uniqueConstraints = @UniqueConstraint(columnNames = "domain"))
@Getter
public class TenantDomain {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false, length = 253)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DomainType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DomainStatus status;

    @Setter
    @Column(name = "is_primary", nullable = false)
    private boolean primaryDomain;

    @Column(name = "verification_token", updatable = false, length = 80)
    private String verificationToken;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_check_result", length = 255)
    private String lastCheckResult;

    protected TenantDomain() {}

    public TenantDomain(String tenantId, String domain, DomainType type,
                        DomainStatus status, String verificationToken) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.domain = domain;
        this.type = type;
        this.status = status;
        this.verificationToken = verificationToken;
        this.createdAt = Instant.now();

        if (status == DomainStatus.VERIFIED) {
            this.verifiedAt = this.createdAt;
        }
    }

    public void markVerified() {
        this.status = DomainStatus.VERIFIED;
        this.verifiedAt = Instant.now();
    }

    public void recordCheck(String result) {
        this.lastCheckedAt = Instant.now();
        this.lastCheckResult = result == null || result.length() <= 255
                ? result
                : result.substring(0, 255);
    }
}