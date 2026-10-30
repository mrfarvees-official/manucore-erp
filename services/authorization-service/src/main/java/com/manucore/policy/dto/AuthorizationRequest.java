package com.manucore.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorizationRequest(
        @NotBlank
        @Size(max = 100)
        String action,

        @NotBlank
        @Size(max = 150)
        String resource
) {
}
