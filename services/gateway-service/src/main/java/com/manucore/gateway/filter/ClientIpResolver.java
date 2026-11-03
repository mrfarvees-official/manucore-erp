package com.manucore.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves the real client address. X-Forwarded-For is only trusted for the
 * configured number of proxy hops, counted from the right, because the left
 * side of that header is client-controlled.
 */
@Component
public class ClientIpResolver {

    private final int trustedProxyHops;

    public ClientIpResolver(
            @Value("${manucore.security.trusted-proxy-hops:0}") int trustedProxyHops) {
        this.trustedProxyHops = Math.max(0, trustedProxyHops);
    }

    public String resolve(ServerHttpRequest request) {

        if (trustedProxyHops > 0) {
            List<String> chain = request.getHeaders().getOrEmpty("X-Forwarded-For").stream()
                    .flatMap(value -> Arrays.stream(value.split(",")))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .toList();

            int index = chain.size() - trustedProxyHops;
            if (index >= 0) {
                return sanitize(chain.get(index));
            }
        }

        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return null;
        }
        return sanitize(remote.getAddress().getHostAddress());
    }

    private static String sanitize(String ip) {
        int scope = ip.indexOf('%');
        String value = scope >= 0 ? ip.substring(0, scope) : ip;
        return value.length() <= 45 && value.matches("[0-9a-fA-F:.]+") ? value : null;
    }
}