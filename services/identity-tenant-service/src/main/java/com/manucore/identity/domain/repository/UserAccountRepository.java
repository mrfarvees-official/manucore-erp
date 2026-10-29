package com.manucore.identity.domain.repository;

import com.manucore.identity.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    boolean existsByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    List<UserAccount> findByTenantId(String tenantId);

    long countByTenantId(String tenantId);
}