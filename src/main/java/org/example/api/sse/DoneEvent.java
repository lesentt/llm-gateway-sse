package org.example.api.sse;

public record DoneEvent(
        String requestId,
        long latencyMs,
        Integer tokenEstimated,
        Double costEstimated
) {
}

