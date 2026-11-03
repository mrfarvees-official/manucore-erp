package com.manucore.identity.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

@Component
public class AuthorizationClient {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationClient.class);

    private final RestClient client;

    public AuthorizationClient(
            @Value("${manucore.authorization.url:http://localhost:8083}") String baseUrl,
            @Value("${manucore.authorization.timeout:PT2S}") Duration timeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Forwards the caller's own token. Empty when the authorization service is unreachable. */
    public Optional<PermissionMatrix> fetchPermissions(String bearerToken) {
        try {
            return Optional.ofNullable(client.get()
                    .uri("/api/v1/authorization/permissions")
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .body(PermissionMatrix.class));
        } catch (RuntimeException e) {
            log.warn("Could not load permissions: {}", e.toString());
            return Optional.empty();
        }
    }
}