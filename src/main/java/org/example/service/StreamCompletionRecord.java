package org.example.service;

import java.math.BigDecimal;
import java.time.Instant;

public record StreamCompletionRecord(
        String requestId,
        String model,
        String status,
        long latencyMs,
        int tokenEstimated,
        BigDecimal costEstimated,
        String costAlgorithmVersion,
        String errorCode,
        String errorMessage,
        boolean clientAborted,
        int attempts,
        int timeoutMs,
        Instant startedAt,
        Instant endedAt,
        String traceId
) {
}
