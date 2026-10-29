package com.manucore.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateItemRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 20) String unit) {
}
