package com.manucore.item.dto;

import com.manucore.item.StockAdjustment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdjustmentResponse(
        UUID id,
        UUID itemId,
        String sku,
        String itemName,
        BigDecimal delta,
        BigDecimal quantityBefore,
        BigDecimal quantityAfter,
        String reason,
        String actorId,
        Instant createdAt) {

    public static AdjustmentResponse from(StockAdjustment a) {
        return new AdjustmentResponse(
                a.getId(),
                a.getItemId(),
                a.getSku(),
                a.getItemName(),
                a.getDelta(),
                a.getQuantityBefore(),
                a.getQuantityAfter(),
                a.getReason(),
                a.getActorId(),
                a.getCreatedAt());
    }
}