package org.example.api;

import org.example.api.dto.ChatMessage;
import org.example.api.dto.ChatStreamRequest;
import org.example.api.error.GatewayErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ChatStreamControllerIT {

    private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> SSE_MAP =
            new ParameterizedTypeReference<>() {
            };

    private final WebTestClient webTestClient;

    @Autowired
    ChatStreamControllerIT(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    @Test
    void stream_shouldReturnMetaDeltaDone() {
        ChatStreamRequest req = new ChatStreamRequest(
                "req_test_success",
                "mock",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                2000,
                null
        );

        FluxExchangeResult<ServerSentEvent<Map<String, Object>>> result = webTestClient.post()
                .uri("/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)//设置请求体为JSON
                .accept(MediaType.TEXT_EVENT_STREAM)//设置响应体为SSE
                .bodyValue(req)
                .exchange()//发送请求
                .expectStatus().isOk()//期望响应状态为OK
                .returnResult(SSE_MAP);

        StepVerifier.create(result.getResponseBody().collectList())
                .assertNext(events -> {
                    Assertions.assertTrue(events.size() >= 3);
                    Assertions.assertEquals("meta", events.getFirst().event());

                    Map<String, Object> meta = events.getFirst().data();
                    Assertions.assertNotNull(meta);
                    Assertions.assertEquals("req_test_success", meta.get("requestId"));

                    boolean hasDelta = events.stream().anyMatch(e -> "delta".equals(e.event()));
                    Assertions.assertTrue(hasDelta);

                    ServerSentEvent<Map<String, Object>> last = events.getLast();
                    Assertions.assertEquals("done", last.event());
                    Assertions.assertNotNull(last.data());
                    Assertions.assertEquals("req_test_success", last.data().get("requestId"));
                })
                .verifyComplete();
    }

    @Test
    void stream_timeoutShouldReturnErrorEvent() {
        ChatStreamRequest req = new ChatStreamRequest(
                "req_test_timeout",
                "mock",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                1,
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

        StepVerifier.create(result.getResponseBody().collectList())
                .assertNext(events -> {
                    Assertions.assertEquals(2, events.size());
                    Assertions.assertEquals("meta", events.getFirst().event());
                    Assertions.assertEquals("error", events.getLast().event());

                    Map<String, Object> errorEvent = events.getLast().data();
                    Assertions.assertNotNull(errorEvent);
                    Object errorObj = errorEvent.get("error");
                    Assertions.assertInstanceOf(Map.class, errorObj);
                    Map<?, ?> error = (Map<?, ?>) errorObj;
                    Assertions.assertEquals("req_test_timeout", error.get("requestId"));
                    Assertions.assertEquals(GatewayErrorCode.UPSTREAM_TIMEOUT.name(), error.get("code"));
                })
                .verifyComplete();
    }

    @Test
    void stream_upstreamErrorShouldReturnErrorEvent() {
        ChatStreamRequest req = new ChatStreamRequest(
                "req_test_upstream_error",
                "mock_error",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                2000,
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

        StepVerifier.create(result.getResponseBody().collectList())
                .assertNext(events -> {
                    Assertions.assertTrue(events.size() >= 2);
                    Assertions.assertEquals("meta", events.getFirst().event());
                    Assertions.assertEquals("error", events.getLast().event());

                    Map<String, Object> errorEvent = events.getLast().data();
                    Assertions.assertNotNull(errorEvent);
                    Object errorObj = errorEvent.get("error");
                    Assertions.assertInstanceOf(Map.class, errorObj);
                    Map<?, ?> error = (Map<?, ?>) errorObj;
                    Assertions.assertEquals("req_test_upstream_error", error.get("requestId"));
                    Assertions.assertEquals(GatewayErrorCode.UPSTREAM_ERROR.name(), error.get("code"));
                })
                .verifyComplete();
    }
}
