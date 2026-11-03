package com.manucore.gateway.authz;

import com.manucore.gateway.filter.CorrelationIdFilter;
import com.manucore.gateway.filter.TenantHeaderFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Every request to every downstream service is checked by the authorization service.
 *
 * Exempt: login/JWKS (/api/v1/auth/**), the authorization service itself
 * (it guards its own endpoints), and actuator probes.
 *
 * Not active in the dev-open profile, which disables authentication entirely.
 */
@Component
@Profile("!dev-open")
public class AuthorizationFilter implements GlobalFilter, Ordered {

    private final AuthorizationClient client;

    public AuthorizationFilter(AuthorizationClient client) {
        this.client = client;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isExempt(path)) {
            return chain.filter(exchange);
        }

        String method = request.getMethod().name();

        Optional<AuthorizationTargetResolver.Target> target =
                AuthorizationTargetResolver.resolve(method, path);

        if (target.isEmpty()) {
            return reject(exchange, HttpStatus.FORBIDDEN,
                    "This request is not mapped to a permission.");
        }

        String clientIp = request.getHeaders().getFirst(TenantHeaderFilter.CLIENT_IP_HEADER);
        String correlationId = request.getHeaders().getFirst(CorrelationIdFilter.HEADER);

        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication()))
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> client.check(
                        auth.getToken().getTokenValue(),
                        target.get(),
                        clientIp,
                        method,
                        correlationId))
                .defaultIfEmpty(AuthorizationClient.Verdict.UNAUTHENTICATED)
                .flatMap(verdict -> switch (verdict) {
                    case ALLOW -> chain.filter(exchange);
                    case DENY -> reject(exchange, HttpStatus.FORBIDDEN,
                            "Access denied.");
                    case UNAUTHENTICATED -> reject(exchange, HttpStatus.UNAUTHORIZED,
                            "Authentication required.");
                    case UNAVAILABLE -> reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            "Authorization is temporarily unavailable.");
                });
    }

    private static boolean isExempt(String path) {
        return path.equals("/api/v1/auth") || path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/authorization") || path.startsWith("/api/v1/authorization/")
                || path.startsWith("/actuator/");
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"status\":" + status.value() + ",\"error\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    // After CorrelationIdFilter (-2) and TenantHeaderFilter (-1), before routing
    @Override
    public int getOrder() {
        return 0;
    }
}