package org.example.api;

import org.example.api.dto.ChatMessage;
import org.example.api.dto.ChatStreamRequest;
import org.example.upstream.UpstreamChatClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(ChatStreamCancelIT.CancelUpstreamConfig.class)
class ChatStreamCancelIT {

    private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> SSE_MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void cancelShouldPropagateToUpstream() {
        CancelUpstreamConfig.CANCELLED.set(false);

        ChatStreamRequest req = new ChatStreamRequest(
                "req_test_cancel",
                "mock",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                10_000,
                null
        );

        FluxExchangeResult<ServerSentEvent<Map<String, Object>>> result = webTestClient.post()
                .uri("/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_MAP);

        StepVerifier.create(result.getResponseBody())
                .assertNext(e -> Assertions.assertEquals("meta", e.event()))
                .assertNext(e -> Assertions.assertEquals("delta", e.event()))
                .thenCancel()
                .verify(Duration.ofSeconds(2));

        Assertions.assertTrue(CancelUpstreamConfig.CANCELLED.get());
    }

    @TestConfiguration
    static class CancelUpstreamConfig {
        static final AtomicBoolean CANCELLED = new AtomicBoolean(false);

        @Bean
        @Primary
        UpstreamChatClient upstreamChatClient() {
            return (request, requestId) -> Flux.interval(Duration.ofMillis(20))
                    .map(i -> "x")
                    .doOnCancel(() -> CANCELLED.set(true));
        }
    }
}

