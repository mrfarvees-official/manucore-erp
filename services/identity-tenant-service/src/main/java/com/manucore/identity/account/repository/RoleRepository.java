package com.manucore.identity.account.repository;

import com.manucore.identity.account.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<Role> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(String tenantId, String name);
}