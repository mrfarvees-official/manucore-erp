package com.manucore.identity.auth;

import java.util.List;

public record MeResponse(
    String id,
    String email,
    String name,
    List<String> roles
) {
}
