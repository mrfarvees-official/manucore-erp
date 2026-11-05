package com.manucore.identity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_roles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@Getter
public class Role {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false, length = 50)
    private String name;

    @Setter
    @Column(length = 255)
    private String description;

    @Column(name = "system_role", nullable = false, updatable = false)
    private boolean systemRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Role() {}

    public Role(String tenantId, String name, String description, boolean systemRole) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
        this.createdAt = Instant.now();
    }
}