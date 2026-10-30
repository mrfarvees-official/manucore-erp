package com.manucore.policy.repository;

import com.manucore.policy.PolicyCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyConditionRepository extends JpaRepository<PolicyCondition, UUID> {

    List<PolicyCondition> findByConditionGroupId(UUID conditionGroupId);
}