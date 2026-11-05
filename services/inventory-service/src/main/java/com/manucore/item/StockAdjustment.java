package com.manucore.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustments", indexes = {
        @Index(name = "idx_stock_adjustments_tenant_created", columnList = "tenant_id, created_at"),
        @Index(name = "idx_stock_adjustments_item", columnList = "item_id")})
@Getter
public class StockAdjustment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(name = "item_name", nullable = false, updatable = false)
    private String itemName;

    @Column(nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal delta;

    @Column(name = "quantity_before", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal quantityAfter;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "actor_id", updatable = false)
    private String actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockAdjustment() {}

    public StockAdjustment(Item item,
                           BigDecimal delta,
                           BigDecimal quantityBefore,
                           BigDecimal quantityAfter,
                           String reason,
                           String actorId) {
        this.id = UUID.randomUUID();
        this.tenantId = item.getTenantId();
        this.itemId = item.getId();
        this.sku = item.getSku();
        this.itemName = item.getName();
        this.delta = delta;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.reason = reason;
        this.actorId = actorId;
        this.createdAt = Instant.now();
    }
}