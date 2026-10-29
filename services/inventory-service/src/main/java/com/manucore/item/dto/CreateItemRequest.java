package com.manucore.item.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateItemRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @PositiveOrZero BigDecimal quantity) {
}
