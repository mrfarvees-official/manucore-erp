package com.manucore.item.dto;

import com.manucore.item.Item;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        String tenantId,
        String sku,
        String name,
        String unit,
        BigDecimal quantity,
        Instant createdAt,
        Instant updatedAt) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getTenantId(),
                item.getSku(),
                item.getName(),
                item.getUnit(),
                item.getQuantity(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
