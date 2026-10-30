package com.manucore.policy.repository;

import com.manucore.policy.PolicySubject;
import com.manucore.policy.enums.SubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicySubjectRepository extends JpaRepository<PolicySubject, UUID> {

    List<PolicySubject> findByTenantIdAndSubjectTypeAndSubjectId(
            String tenantId,
            SubjectType subjectType,
            String subjectId
    );
}
