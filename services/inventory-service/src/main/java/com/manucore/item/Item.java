package com.manucore.item;

import com.manucore.item.dto.ItemType;
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

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 30)
    private ItemType type;

    @Column(length = 1000)
    private String description;

    @Column(name = "reorder_level", precision = 19, scale = 3)
    private BigDecimal reorderLevel;

    /* Null means active. A timestamp avoids a NOT NULL column on existing rows. */
    @Column(name = "archived_at")
    private Instant archivedAt;

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
        this.tenantId = tenantId;
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

    public void updateCatalog(String category, ItemType type, String description, BigDecimal reorderLevel) {
        this.category = blankToNull(category);
        this.type = type;
        this.description = blankToNull(description);
        this.reorderLevel = reorderLevel;
    }

    public void setArchived(boolean archived) {
        if (archived) {
            if (archivedAt == null) {
                archivedAt = Instant.now();
            }
        } else {
            archivedAt = null;
        }
    }

    public boolean isActive() {
        return archivedAt == null;
    }

    /* Above zero but at or below the reorder level. Zero stock is "out", not "low". */
    public boolean isLowStock() {
        return reorderLevel != null
                && quantity.signum() > 0
                && quantity.compareTo(reorderLevel) <= 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
