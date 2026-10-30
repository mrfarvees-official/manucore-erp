package com.manucore.policy.repository;

import com.manucore.policy.PolicyConditionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyConditionGroupRepository extends JpaRepository<PolicyConditionGroup, UUID> {

    List<PolicyConditionGroup> findByPolicyId(UUID policyId);

    List<PolicyConditionGroup> findByParentGroupId(UUID parentGroupId);
}
