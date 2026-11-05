package com.manucore.item.dto;

import com.manucore.item.Item;
import com.manucore.item.dto.ItemType;

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
        String category,
        ItemType type,
        String description,
        BigDecimal reorderLevel,
        boolean active,
        boolean lowStock,
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
                item.getCategory(),
                item.getType(),
                item.getDescription(),
                item.getReorderLevel(),
                item.isActive(),
                item.isLowStock(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}