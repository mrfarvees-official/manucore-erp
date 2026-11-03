package com.manucore.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthorizationRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_.-]+")
        String action,

        @NotBlank @Size(max = 150) @Pattern(regexp = "[A-Za-z0-9_.-]+")
        String resource
) {
}