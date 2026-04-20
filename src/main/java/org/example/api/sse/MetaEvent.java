package org.example.api.sse;

public record MetaEvent(
        String requestId,
        String model,
        String startedAt
) {
}

