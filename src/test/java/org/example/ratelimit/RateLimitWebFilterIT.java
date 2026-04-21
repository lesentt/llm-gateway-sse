package org.example.ratelimit;

import org.example.api.dto.ChatMessage;
import org.example.api.dto.ChatStreamRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "llm-gateway.ratelimit.enabled=true",
                "llm-gateway.ratelimit.rpm=2",
                "llm-gateway.security.api-key.enabled=true",
                "llm-gateway.security.api-key.pepper=",
                "llm-gateway.security.api-key.hashes[0]=d5f6f9830b62983c1ebd9e27ff5d495726be1c5366e529fd9ec05efe3fc377e6"
        }
)
@AutoConfigureWebTestClient
@Import(RateLimitWebFilterIT.InMemoryStoreConfig.class)
class RateLimitWebFilterIT {

    private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> SSE_MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void reset() {
        InMemoryStoreConfig.STORE.clear();
    }

    @Test
    void shouldReturn429WhenExceedLimit() {
        ChatStreamRequest req = new ChatStreamRequest(
                "req_rl_001",
                "mock",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                2000,
                null
        );

        // 1st request OK (SSE)
        FluxExchangeResult<ServerSentEvent<Map<String, Object>>> r1 = webTestClient.post()
                .uri("/v1/chat/stream")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test_key")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_MAP);
        StepVerifier.create(r1.getResponseBody().take(1))
                .assertNext(e -> Assertions.assertEquals("meta", e.event()))
                .thenCancel()
                .verify();

        // 2nd request OK (SSE)
        FluxExchangeResult<ServerSentEvent<Map<String, Object>>> r2 = webTestClient.post()
                .uri("/v1/chat/stream")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test_key")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_MAP);
        StepVerifier.create(r2.getResponseBody().take(1))
                .assertNext(e -> Assertions.assertEquals("meta", e.event()))
                .thenCancel()
                .verify();

        // 3rd request rate-limited (JSON)
        webTestClient.post()
                .uri("/v1/chat/stream")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test_key")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> {
                    Assertions.assertTrue(body.containsKey("error"));
                    Map<?, ?> error = (Map<?, ?>) body.get("error");
                    Assertions.assertEquals("RATE_LIMITED", error.get("code"));
                    Assertions.assertNotNull(error.get("requestId"));
                });
    }

    @TestConfiguration
    static class InMemoryStoreConfig {
        static final ConcurrentMap<String, AtomicLong> STORE = new ConcurrentHashMap<>();

        @Bean
        @Primary
        RateLimitStore rateLimitStore() {
            return (key, ttl) -> Mono.fromSupplier(() -> STORE.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet());
        }
    }
}

