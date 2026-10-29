package com.manucore.item;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "items", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "sku"}))
@Getter
@Setter
public class Item {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    /* Optimistic locking: two parallel stock adjustments cannot silently overwrite each other. */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Item() {}

    public Item(String tenantId,
                String sku,
                @NotBlank @Size(max = 200) String name,
                @NotBlank @Size(max = 20) String unit,
                @NotNull @PositiveOrZero BigDecimal quantity) {
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.name = name;
        this.unit = unit;
        this.quantity = quantity;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @PreDestroy
    void onDelete() {
        deletedAt = Instant.now();
    }

    public void rename(String name, String unit) {
        this.name = name;
        this.unit = unit;
    }
}
