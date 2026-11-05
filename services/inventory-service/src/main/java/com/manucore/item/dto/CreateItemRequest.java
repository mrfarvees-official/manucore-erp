package com.manucore.item.dto;

import com.manucore.item.dto.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateItemRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @PositiveOrZero BigDecimal quantity,
        @Size(max = 100) String category,
        ItemType type,
        @Size(max = 1000) String description,
        @PositiveOrZero BigDecimal reorderLevel) {

    public CreateItemRequest(String sku, String name, String unit, BigDecimal quantity) {
        this(sku, name, unit, quantity, null, null, null, null);
    }
}