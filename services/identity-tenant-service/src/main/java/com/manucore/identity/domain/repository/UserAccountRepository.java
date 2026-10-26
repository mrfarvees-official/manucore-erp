package com.manucore.identity.domain.repository;

import com.manucore.identity.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByTenantAndEmailIgnoreCase(String tenantId, String email);

    boolean existsByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    List<UserAccount> findByTenant(String tenantId);

    List<UserAccount> findByEmailIgnoreCase(String email);

    List<UserAccount> findByActive(boolean active);

    String getTenantId();

    long countByTenant(String tenantId);

    UserAccount create(UserAccount user);

    UserAccount getById(UUID id);

    UserAccount updateById(UUID id, UserAccount user);

    void deleteById(UUID id);
}
