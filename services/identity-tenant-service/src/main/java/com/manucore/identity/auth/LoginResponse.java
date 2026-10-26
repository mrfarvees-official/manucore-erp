package com.manucore.identity.auth;

public record LoginResponse (
    String accessToken,
    String tokenType,
    long expiresInSeconds){
}
