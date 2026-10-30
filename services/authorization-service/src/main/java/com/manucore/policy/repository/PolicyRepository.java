package com.manucore.policy.repository;

import com.manucore.policy.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    List<Policy> findByTenantIdAndActiveTrue(String tenantId);
}
