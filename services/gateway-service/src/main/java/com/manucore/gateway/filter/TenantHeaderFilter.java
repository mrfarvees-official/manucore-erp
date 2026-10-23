package com.manucore.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.swing.text.html.Option;
import java.util.Optional;

@Component
public class TenantHeaderFilter implements GlobalFilter, Ordered {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String USER_HEADER = "X-User-Id";

    private final String tenantClaim;
    private final String devTenantId;

    public TenantHeaderFilter(
            @Value("${manucore.security.tenant-claim:tenant_id}") String tenantClaim,
            @Value("${manucore.security.dev-tenant-id:}") String devTenantId) {
        this.tenantClaim = tenantClaim;
        this.devTenantId = devTenantId;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication()))
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(auth -> auth.isPresent()
                        ? handleJwt(exchange, chain, auth.get().getToken())
                        : chain.filter(withIdentity(exchange, devTenantId, null)));
    }

    private Mono<Void> handleJwt(ServerWebExchange exchange, GatewayFilterChain chain, Jwt jwt) {
        String tenant = jwt.getClaimAsString(tenantClaim);
        if (tenant == null || tenant.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(withIdentity(exchange, devTenantId, null));
    }

    // Removes any client supplied identity headers, then sets trusted ones
    private ServerWebExchange withIdentity(ServerWebExchange exchange, String tenantId, String userId) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(TENANT_HEADER);
                    headers.remove(USER_HEADER);
                    if (tenantId != null && !tenantId.isBlank()) {
                        headers.set(TENANT_HEADER, tenantId);
                    }
                    if (userId != null && !userId.isBlank()) {
                        headers.set(USER_HEADER, userId);
                    }
                })
                .build();

        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder()
    {
        return -1;
    }
}
