package com.manucore.identity.audit.repository;

import com.manucore.identity.audit.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
            select e from AuditEvent e
            where e.tenantId = :tenantId
              and (:targetType = '' or e.targetType = :targetType)
              and (:targetId = '' or e.targetId = :targetId)
              and (:actorId = '' or e.actorId = :actorId)
              and (:action = '' or e.action = :action)
              and (lower(coalesce(e.targetLabel, '')) like :pattern escape '!'
                   or lower(coalesce(e.actorName, '')) like :pattern escape '!'
                   or lower(e.action) like :pattern escape '!')
            order by e.occurredAt desc
            """)
    Page<AuditEvent> search(@Param("tenantId") String tenantId,
                            @Param("pattern") String pattern,
                            @Param("targetType") String targetType,
                            @Param("targetId") String targetId,
                            @Param("actorId") String actorId,
                            @Param("action") String action,
                            Pageable pageable);
}