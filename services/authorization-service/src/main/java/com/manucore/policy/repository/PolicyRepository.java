package com.manucore.policy.repository;

import com.manucore.policy.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    List<Policy> findByTenantId(String tenantId);

    @Query("""
        select distinct p from Policy p
          join fetch p.action a
          join fetch p.resource r
          left join fetch p.subjects
        where p.tenantId = :tenantId
          and lower(a.name) = lower(:actionName)
          and lower(r.name) = lower(:resourceName)
        """)
    List<Policy> findCandidates(@Param("tenantId") String tenantId,
                                @Param("actionName") String actionName,
                                @Param("resourceName") String resourceName);

}
