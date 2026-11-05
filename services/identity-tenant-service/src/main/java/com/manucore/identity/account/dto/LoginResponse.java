package com.manucore.identity.account.dto;

public record LoginResponse (
    String accessToken,
    String tokenType,
    long expiresInSeconds){
}
