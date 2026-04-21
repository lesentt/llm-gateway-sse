package org.example.service;

import org.example.api.dto.ChatStreamRequest;
import org.example.api.error.ErrorResponse;
import org.example.api.error.GatewayErrorCode;
import org.example.api.sse.DeltaEvent;
import org.example.api.sse.DoneEvent;
import org.example.api.sse.MetaEvent;
import org.example.upstream.UpstreamChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_TIMEOUT_MS = 120_000;

    private final UpstreamChatClient upstreamChatClient;

    public ChatStreamService(UpstreamChatClient upstreamChatClient) {
        this.upstreamChatClient = upstreamChatClient;
    }

    public Flux<ServerSentEvent<?>> stream(ChatStreamRequest request, String requestId) {
        long startNanos = System.nanoTime();

        String model = request.model() != null ? request.model() : "mock";
        int timeoutMs = normalizeTimeoutMs(request.timeoutMs());
        String startedAt = Instant.now().toString();

        ServerSentEvent<MetaEvent> meta = ServerSentEvent.<MetaEvent>builder()
                .event("meta")
                .data(new MetaEvent(requestId, model, startedAt))
                .build();

        AtomicInteger index = new AtomicInteger(0);
        AtomicInteger totalChars = new AtomicInteger(0);

        Flux<ServerSentEvent<?>> deltas = upstreamChatClient.stream(request, requestId)
                .doFinally(signalType -> log.info("requestId={} upstreamSignal={}", requestId, signalType))
                .timeout(Duration.ofMillis(timeoutMs))
                .map(chunk -> {
                    totalChars.addAndGet(chunk != null ? chunk.length() : 0);
                    return ServerSentEvent.<DeltaEvent>builder()
                            .event("delta")
                            .data(new DeltaEvent(index.getAndIncrement(), chunk))
                            .build();
                });

        Flux<ServerSentEvent<?>> done = Flux.defer(() -> {
            long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            int tokenEstimated = Math.max(1, totalChars.get() / 4);
            double costEstimated = tokenEstimated * 0.000001d;
            return Flux.just(ServerSentEvent.<DoneEvent>builder()
                    .event("done")
                    .data(new DoneEvent(requestId, latencyMs, tokenEstimated, costEstimated))
                    .build());
        });

        return Flux.concat(Flux.just(meta), deltas, done)
                .onErrorResume(ex -> Flux.just(toErrorEvent(ex, requestId)))
                .doOnCancel(() -> log.info("requestId={} clientAborted=true", requestId));
    }

    private static int normalizeTimeoutMs(Integer timeoutMs) {
        if (timeoutMs == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(Math.max(1, timeoutMs), MAX_TIMEOUT_MS);
    }

    private static ServerSentEvent<ErrorResponse> toErrorEvent(Throwable ex, String requestId) {
        GatewayErrorCode code;
        String message;

        if (ex instanceof TimeoutException) {
            code = GatewayErrorCode.UPSTREAM_TIMEOUT;
            message = "Upstream timeout, please retry.";
        } else {
            code = GatewayErrorCode.UPSTREAM_ERROR;
            message = "Upstream error, please retry.";
        }

        return ServerSentEvent.<ErrorResponse>builder()
                .event("error")
                .data(ErrorResponse.of(code, message, requestId))
                .build();
    }
}
