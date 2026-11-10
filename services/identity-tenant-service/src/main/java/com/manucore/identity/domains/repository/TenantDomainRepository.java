package com.manucore.identity.domains.repository;

import com.manucore.identity.domains.TenantDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, UUID> {

    List<TenantDomain> findByTenantIdOrderByPrimaryDomainDescDomainAsc(String tenantId);

    Optional<TenantDomain> findByIdAndTenantId(UUID id, String tenantId);

    Optional<TenantDomain> findByDomainIgnoreCase(String domain);

    boolean existsByDomainIgnoreCase(String domain);

    long countByTenantId(String tenantId);
}