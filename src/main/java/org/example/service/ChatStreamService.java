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
import org.slf4j.MDC;
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
    private final TokenCostEstimator tokenCostEstimator;
    private final StreamCompletionRecorder streamCompletionRecorder;

    public ChatStreamService(
            UpstreamChatClient upstreamChatClient,
            MeterRegistry meterRegistry,
            TokenCostEstimator tokenCostEstimator,
            StreamCompletionRecorder streamCompletionRecorder
    ) {
        this.upstreamChatClient = upstreamChatClient;
        this.meterRegistry = meterRegistry;
        this.tokenCostEstimator = tokenCostEstimator;
        this.streamCompletionRecorder = streamCompletionRecorder;
    }

    public Flux<ServerSentEvent<?>> stream(ChatStreamRequest request, String requestId) {
        long startNanos = System.nanoTime();
        Instant startedAt = Instant.now();

        String model = upstreamChatClient.resolveModel(request);
        int timeoutMs = normalizeTimeoutMs(request.timeoutMs());
        String startedAtText = startedAt.toString();

        AtomicReference<String> finalStatusForMetrics = new AtomicReference<>("done");
        AtomicReference<GatewayErrorCode> finalErrorCode = new AtomicReference<>();
        AtomicReference<String> finalErrorMessage = new AtomicReference<>();
        AtomicReference<UsageEstimate> usageEstimate = new AtomicReference<>(tokenCostEstimator.estimate(0));

        ServerSentEvent<MetaEvent> meta = ServerSentEvent.<MetaEvent>builder()
                .event("meta")
                .data(new MetaEvent(requestId, model, startedAtText))
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
            UsageEstimate estimate = tokenCostEstimator.estimate(totalChars.get());
            usageEstimate.set(estimate);
            return Flux.just(ServerSentEvent.<DoneEvent>builder()
                    .event("done")
                    .data(new DoneEvent(requestId, latencyMs, estimate.tokenEstimated(), estimate.costEstimated().doubleValue()))
                    .build());
        });

        return Flux.concat(Flux.just(meta), deltas, done)
                .onErrorResume(ex -> {
                    finalStatusForMetrics.set("error");
                    GatewayErrorCode code = resolveErrorCode(ex);
                    finalErrorCode.set(code);
                    finalErrorMessage.set(resolveErrorMessage(code));
                    usageEstimate.set(tokenCostEstimator.estimate(totalChars.get()));
                    return Flux.just(toErrorEvent(code, requestId));
                })
                .doOnCancel(() -> {
                    finalStatusForMetrics.set("cancel");
                    log.info("requestId={} clientAborted=true", requestId);
                })
                .doFinally(signalType -> {
                    Duration latency = Duration.ofNanos(System.nanoTime() - startNanos);
                    UsageEstimate finalEstimate = usageEstimate.get();
                    Instant endedAt = Instant.now();
                    String status = resolveRecordStatus(finalErrorCode.get(), finalStatusForMetrics.get());
                    String traceId = MDC.get("traceId");
                    log.info(
                            "requestId={} status={} latencyMs={} tokenEstimated={} costEstimated={} costAlgorithmVersion={} errorCode={} clientAborted={}",
                            requestId,
                            status,
                            latency.toMillis(),
                            finalEstimate.tokenEstimated(),
                            finalEstimate.costEstimated(),
                            finalEstimate.algorithmVersion(),
                            finalErrorCode.get() != null ? finalErrorCode.get().name() : "",
                            "cancel".equals(finalStatusForMetrics.get())
                    );
                    streamCompletionRecorder.record(new StreamCompletionRecord(
                            requestId,
                            model,
                            status,
                            latency.toMillis(),
                            finalEstimate.tokenEstimated(),
                            finalEstimate.costEstimated(),
                            finalEstimate.algorithmVersion(),
                            finalErrorCode.get() != null ? finalErrorCode.get().name() : null,
                            finalErrorMessage.get(),
                            "cancel".equals(finalStatusForMetrics.get()),
                            1,
                            timeoutMs,
                            startedAt,
                            endedAt,
                            traceId
                    ));
                    recordMetrics(model, finalStatusForMetrics.get(), latency);
                });
    }

    private static int normalizeTimeoutMs(Integer timeoutMs) {
        if (timeoutMs == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(Math.max(1, timeoutMs), MAX_TIMEOUT_MS);
    }

    private static ServerSentEvent<ErrorResponse> toErrorEvent(GatewayErrorCode code, String requestId) {
        return ServerSentEvent.<ErrorResponse>builder()
                .event("error")
                .data(ErrorResponse.of(code, resolveErrorMessage(code), requestId))
                .build();
    }

    private static GatewayErrorCode resolveErrorCode(Throwable ex) {
        if (ex instanceof TimeoutException) {
            return GatewayErrorCode.UPSTREAM_TIMEOUT;
        }
        return GatewayErrorCode.UPSTREAM_ERROR;
    }

    private static String resolveErrorMessage(GatewayErrorCode code) {
        if (code == GatewayErrorCode.UPSTREAM_TIMEOUT) {
            return "Upstream timeout, please retry.";
        }
        return "Upstream error, please retry.";
    }

    private static String resolveRecordStatus(GatewayErrorCode code, String finalStatusForMetrics) {
        if ("cancel".equals(finalStatusForMetrics)) {
            return "canceled";
        }
        if (code == null) {
            return "ok";
        }
        if (code == GatewayErrorCode.UPSTREAM_TIMEOUT) {
            return "timeout";
        }
        return "error";
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
