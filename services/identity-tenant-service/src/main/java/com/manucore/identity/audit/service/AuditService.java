package com.manucore.identity.audit.service;

import com.manucore.identity.audit.AuditEvent;
import com.manucore.identity.audit.FieldChange;
import com.manucore.identity.audit.dto.Actor;
import com.manucore.identity.audit.dto.AuditDtos.AuditEventResponse;
import com.manucore.identity.audit.dto.AuditDtos.AuditPage;
import com.manucore.identity.audit.repository.AuditEventRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
public class AuditService {

    private final AuditEventRepository events;

    /**
     * Joins the caller's transaction, so the change and its trace are saved together:
     * if the trace cannot be written, the change is rolled back too.
     */
    @Transactional
    public void record(String tenantId, Actor actor, String action, String targetType,
                       String targetId, String targetLabel, List<FieldChange> changes) {
        events.save(new AuditEvent(tenantId, actor, action, targetType, targetId, targetLabel, changes));
    }

    @Transactional(readOnly = true)
    public AuditPage search(String tenantId, String query, String targetType, String targetId,
                            String actorId, String action, int page, int size) {
        String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";

        Page<AuditEvent> result = events.search(
                tenantId, pattern,
                targetType.trim().toUpperCase(Locale.ROOT),
                targetId.trim(),
                actorId.trim(),
                action.trim().toUpperCase(Locale.ROOT),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));

        return new AuditPage(
                result.getContent().stream().map(AuditEventResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}