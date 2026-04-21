package org.example.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_TIMEOUT_MS = 120_000;

    private final UpstreamChatClient upstreamChatClient;
    private final MeterRegistry meterRegistry;

    public ChatStreamService(UpstreamChatClient upstreamChatClient, MeterRegistry meterRegistry) {
        this.upstreamChatClient = upstreamChatClient;
        this.meterRegistry = meterRegistry;
    }

    public Flux<ServerSentEvent<?>> stream(ChatStreamRequest request, String requestId) {
        long startNanos = System.nanoTime();

        String model = request.model() != null ? request.model() : "mock";
        int timeoutMs = normalizeTimeoutMs(request.timeoutMs());
        String startedAt = Instant.now().toString();

        AtomicReference<String> finalStatus = new AtomicReference<>("done");

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
                .onErrorResume(ex -> {
                    finalStatus.set("error");
                    return Flux.just(toErrorEvent(ex, requestId));
                })
                .doOnCancel(() -> {
                    finalStatus.set("cancel");
                    log.info("requestId={} clientAborted=true", requestId);
                })
                .doFinally(signalType -> recordMetrics(
                        model,
                        finalStatus.get(),
                        Duration.ofNanos(System.nanoTime() - startNanos)
                ));
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

    private void recordMetrics(String model, String status, Duration latency) {
        Counter.builder("gateway_chat_stream_requests_total")
                .description("Chat stream requests total")
                .tag("model", model)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        Timer.builder("gateway_chat_stream_latency")
                .description("Chat stream end-to-end latency")
                .tag("model", model)
                .tag("status", status)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(latency);
    }
}

