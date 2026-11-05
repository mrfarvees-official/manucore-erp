package com.manucore.item.dto;

import java.util.List;

public record CatalogSummary(
        long activeItems,
        long lowStock,
        long outOfStock,
        List<String> categories) {
}