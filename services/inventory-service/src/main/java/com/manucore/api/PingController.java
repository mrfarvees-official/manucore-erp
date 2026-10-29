package com.manucore.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/inventory")
public class PingController {

    @GetMapping("/ping")
    public PingResponse ping(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        return new PingResponse(
                "inventory-service",
                "UP",
                tenantId,
                userId,
                correlationId,
                Instant.now());
    }
}
