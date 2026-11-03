package com.manucore.gateway.authz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.manucore.gateway.filter.TenantHeaderFilter.CLIENT_IP_HEADER;
import static com.manucore.gateway.filter.TenantHeaderFilter.ORIGINAL_METHOD_HEADER;

@Component
@Profile("!dev-open")
public class AuthorizationClient {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationClient.class);

    public enum Verdict { ALLOW, DENY, UNAUTHENTICATED, UNAVAILABLE }

    public record CheckRequest(String action, String resource) {
    }

    public record Decision(boolean allowed) {
    }

    private final WebClient webClient;
    private final Duration timeout;

    public AuthorizationClient(
            @Value("${manucore.authorization.url:http://localhost:8083}") String baseUrl,
            @Value("${manucore.authorization.timeout:PT2S}") Duration timeout) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.timeout = timeout;
    }

    /** Always emits exactly one verdict. Anything unexpected becomes UNAVAILABLE (fail closed). */
    public Mono<Verdict> check(
            String bearerToken,
            AuthorizationTargetResolver.Target target,
            String clientIp,
            String originalMethod,
            String correlationId) {

        return webClient.post()
                .uri("/api/v1/authorization/check")
                .headers(headers -> {
                    headers.setBearerAuth(bearerToken);
                    headers.set(ORIGINAL_METHOD_HEADER, originalMethod);
                    if (clientIp != null) {
                        headers.set(CLIENT_IP_HEADER, clientIp);
                    }
                    if (correlationId != null) {
                        headers.set("X-Correlation-Id", correlationId);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CheckRequest(target.action(), target.resource()))
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(Decision.class)
                                .map(decision -> decision.allowed() ? Verdict.ALLOW : Verdict.DENY);
                    }
                    if (response.statusCode().value() == 401) {
                        return response.releaseBody().thenReturn(Verdict.UNAUTHENTICATED);
                    }
                    log.warn("Authorization service answered {}", response.statusCode());
                    return response.releaseBody().thenReturn(Verdict.UNAVAILABLE);
                })
                .defaultIfEmpty(Verdict.UNAVAILABLE)
                .timeout(timeout)
                .onErrorResume(error -> {
                    log.warn("Authorization check failed: {}", error.toString());
                    return Mono.just(Verdict.UNAVAILABLE);
                });
    }
}