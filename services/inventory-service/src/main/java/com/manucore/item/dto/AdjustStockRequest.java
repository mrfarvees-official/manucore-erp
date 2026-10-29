package com.manucore.item.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdjustStockRequest(
        @NotNull BigDecimal delta) {
}
