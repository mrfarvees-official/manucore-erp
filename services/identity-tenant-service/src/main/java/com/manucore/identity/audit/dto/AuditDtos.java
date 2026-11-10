package com.manucore.identity.audit.dto;

import com.manucore.identity.audit.AuditEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditChangeResponse(String field, String from, String to) {
    }

    public record AuditEventResponse(
            UUID id,
            Instant occurredAt,
            String actorId,
            String actorName,
            String action,
            String targetType,
            String targetId,
            String targetLabel,
            List<AuditChangeResponse> changes) {

        /* Reads the lazy changes list, so call it inside a transaction. */
        public static AuditEventResponse from(AuditEvent e) {
            return new AuditEventResponse(
                    e.getId(),
                    e.getOccurredAt(),
                    e.getActorId(),
                    e.getActorName(),
                    e.getAction(),
                    e.getTargetType(),
                    e.getTargetId(),
                    e.getTargetLabel(),
                    e.getChanges().stream()
                            .map(c -> new AuditChangeResponse(c.getField(), c.getOldValue(), c.getNewValue()))
                            .toList());
        }
    }

    public record AuditPage(
            List<AuditEventResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}