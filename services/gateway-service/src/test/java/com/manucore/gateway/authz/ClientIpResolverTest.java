package com.manucore.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static InetSocketAddress socket(String ip) throws Exception {
        return new InetSocketAddress(InetAddress.getByName(ip), 5555);
    }

    @Test
    void usesSocketAddressWhenNoProxyIsTrusted() throws Exception {
        var request = MockServerHttpRequest.get("/")
                .remoteAddress(socket("10.9.8.7"))
                .header("X-Forwarded-For", "1.1.1.1")
                .build();

        assertThat(new ClientIpResolver(0).resolve(request)).isEqualTo("10.9.8.7");
    }

    @Test
    void usesLastForwardedEntryBehindOneTrustedProxy() throws Exception {
        var request = MockServerHttpRequest.get("/")
                .remoteAddress(socket("10.9.8.7"))
                .header("X-Forwarded-For", "6.6.6.6, 203.0.113.5")
                .build();

        // the client-supplied first entry is ignored
        assertThat(new ClientIpResolver(1).resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void fallsBackToSocketWhenHeaderIsMissing() throws Exception {
        var request = MockServerHttpRequest.get("/")
                .remoteAddress(socket("10.9.8.7"))
                .build();

        assertThat(new ClientIpResolver(1).resolve(request)).isEqualTo("10.9.8.7");
    }
}