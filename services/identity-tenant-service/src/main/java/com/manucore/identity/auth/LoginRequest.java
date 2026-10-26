package com.manucore.identity.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String tenantId,
    @NotBlank String email,
    @NotBlank String password) {
}
