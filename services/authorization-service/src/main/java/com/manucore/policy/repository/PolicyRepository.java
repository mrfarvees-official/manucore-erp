package com.manucore.policy.repository;

import com.manucore.policy.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    List<Policy> findByTenantId(String tenantId);

    Page<Policy> findByTenantId(String tenantId, Pageable pageable);

    @EntityGraph(attributePaths = { "action", "resource", "subjects" })
    Optional<Policy> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(String tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(String tenantId, String name, UUID id);

    /** Policies for one action/resource pair, including wildcard ('*') policies. */
    @Query("""
        select distinct p from Policy p
          join fetch p.action a
          join fetch p.resource r
          left join fetch p.subjects
        where p.tenantId = :tenantId
          and (lower(a.name) = lower(:actionName) or a.name = '*')
          and (lower(r.name) = lower(:resourceName) or r.name = '*')
        """)
    List<Policy> findCandidates(@Param("tenantId") String tenantId,
                                @Param("actionName") String actionName,
                                @Param("resourceName") String resourceName);

    /** Everything for a tenant in one query. Used to build permission matrices. */
    @Query("""
        select distinct p from Policy p
          join fetch p.action
          join fetch p.resource
          left join fetch p.subjects
        where p.tenantId = :tenantId
        """)
    List<Policy> findAllForTenant(@Param("tenantId") String tenantId);
}