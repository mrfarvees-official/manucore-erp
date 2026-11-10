package com.manucore.identity.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        /** The host the user signed in on. Binds the login to that domain's company. */
        @Size(max = 300) String host) {
}