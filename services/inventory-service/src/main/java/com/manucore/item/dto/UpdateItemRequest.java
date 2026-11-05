package com.manucore.item.dto;

import com.manucore.item.dto.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateItemRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 20) String unit,
        @Size(max = 100) String category,
        ItemType type,
        @Size(max = 1000) String description,
        @PositiveOrZero BigDecimal reorderLevel,
        Boolean archived) {
}