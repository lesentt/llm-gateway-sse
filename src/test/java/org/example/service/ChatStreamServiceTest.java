package org.example.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.api.dto.ChatMessage;
import org.example.api.dto.ChatStreamRequest;
import org.example.config.GatewayM4Properties;
import org.example.upstream.UpstreamChatClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

class ChatStreamServiceTest {

    @Test
    void streamShouldEmitMetaDeltaDoneAndRecordOk() {
        StreamCompletionRecorder completionRecorder = Mockito.mock(StreamCompletionRecorder.class);
        ChatStreamService service = createService(
                (request, requestId) -> Flux.just("Hello", " ", "world"),
                completionRecorder
        );

        ChatStreamRequest request = new ChatStreamRequest(
                "req_unit_ok",
                "mock",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                1000,
                null
        );

        StepVerifier.create(service.stream(request, "req_unit_ok"))
                .assertNext(event -> Assertions.assertEquals("meta", event.event()))
                .assertNext(event -> Assertions.assertEquals("delta", event.event()))
                .assertNext(event -> Assertions.assertEquals("delta", event.event()))
                .assertNext(event -> Assertions.assertEquals("delta", event.event()))
                .assertNext(event -> {
                    Assertions.assertEquals("done", event.event());
                    Assertions.assertNotNull(event.data());
                })
                .verifyComplete();

        ArgumentCaptor<StreamCompletionRecord> recordCaptor = ArgumentCaptor.forClass(StreamCompletionRecord.class);
        Mockito.verify(completionRecorder, Mockito.timeout(500).times(1)).record(recordCaptor.capture());
        StreamCompletionRecord record = recordCaptor.getValue();
        Assertions.assertEquals("ok", record.status());
        Assertions.assertFalse(record.clientAborted());
        Assertions.assertTrue(record.tokenEstimated() > 0);
    }

    @Test
    void streamShouldEmitErrorAndRecordTimeoutWhenUpstreamTooSlow() {
        StreamCompletionRecorder completionRecorder = Mockito.mock(StreamCompletionRecorder.class);
        ChatStreamService service = createService(
                (request, requestId) -> Flux.just("slow").delayElements(Duration.ofMillis(20)),
                completionRecorder
        );

        ChatStreamRequest request = new ChatStreamRequest(
                "req_unit_timeout",
                "mock_slow",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                1,
                null
        );

        StepVerifier.create(service.stream(request, "req_unit_timeout"))
                .assertNext(event -> Assertions.assertEquals("meta", event.event()))
                .assertNext(event -> Assertions.assertEquals("error", event.event()))
                .verifyComplete();

        ArgumentCaptor<StreamCompletionRecord> recordCaptor = ArgumentCaptor.forClass(StreamCompletionRecord.class);
        Mockito.verify(completionRecorder, Mockito.timeout(500).times(1)).record(recordCaptor.capture());
        StreamCompletionRecord record = recordCaptor.getValue();
        Assertions.assertEquals("timeout", record.status());
        Assertions.assertEquals("UPSTREAM_TIMEOUT", record.errorCode());
    }

    @Test
    void streamShouldRecordCanceledWhenSubscriberCancels() {
        StreamCompletionRecorder completionRecorder = Mockito.mock(StreamCompletionRecorder.class);
        ChatStreamService service = createService(
                (request, requestId) -> Flux.interval(Duration.ofMillis(5)).map(i -> "chunk-" + i),
                completionRecorder
        );

        ChatStreamRequest request = new ChatStreamRequest(
                "req_unit_cancel",
                "mock",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                1000,
                null
        );

        StepVerifier.create(service.stream(request, "req_unit_cancel"))
                .assertNext(event -> Assertions.assertEquals("meta", event.event()))
                .assertNext(event -> Assertions.assertEquals("delta", event.event()))
                .thenCancel()
                .verify();

        ArgumentCaptor<StreamCompletionRecord> recordCaptor = ArgumentCaptor.forClass(StreamCompletionRecord.class);
        Mockito.verify(completionRecorder, Mockito.timeout(500).times(1)).record(recordCaptor.capture());
        StreamCompletionRecord record = recordCaptor.getValue();
        Assertions.assertEquals("canceled", record.status());
        Assertions.assertTrue(record.clientAborted());
    }

    private static ChatStreamService createService(UpstreamChatClient upstreamChatClient, StreamCompletionRecorder completionRecorder) {
        GatewayM4Properties m4Properties = new GatewayM4Properties();
        TokenCostEstimator tokenCostEstimator = new TokenCostEstimator(m4Properties);
        return new ChatStreamService(
                upstreamChatClient,
                new SimpleMeterRegistry(),
                tokenCostEstimator,
                completionRecorder
        );
    }
}
