package com.manucore.identity.audit;

import com.manucore.identity.audit.dto.Actor;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_tenant_time", columnList = "tenant_id, occurred_at"),
        @Index(name = "idx_audit_target", columnList = "tenant_id, target_type, target_id")})
@Getter
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", updatable = false)
    private String actorId;

    @Column(name = "actor_name", updatable = false)
    private String actorName;

    @Column(nullable = false, updatable = false, length = 60)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private String targetId;

    @Column(name = "target_label", updatable = false)
    private String targetLabel;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "audit_event_changes", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    private List<FieldChange> changes = new ArrayList<>();

    protected AuditEvent() {}

    public AuditEvent(String tenantId, Actor actor, String action, String targetType,
                      String targetId, String targetLabel, List<FieldChange> changes) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.occurredAt = Instant.now();
        this.actorId = actor == null ? null : actor.id();
        this.actorName = actor == null ? null : actor.name();
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.changes = new ArrayList<>(changes);
    }
}