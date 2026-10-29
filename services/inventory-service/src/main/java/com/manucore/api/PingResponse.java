package com.manucore.api;

import java.time.Instant;

public record PingResponse(
        String service,
        String status,
        String tenant,
        String user,
        String correlationId,
        Instant timestamp) {
}
