package org.example.security;

import org.example.api.dto.ChatMessage;
import org.example.api.dto.ChatStreamRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "llm-gateway.security.api-key.enabled=true",
                "llm-gateway.security.api-key.pepper=",
                "llm-gateway.security.api-key.hashes[0]=d5f6f9830b62983c1ebd9e27ff5d495726be1c5366e529fd9ec05efe3fc377e6"
        }
)
@AutoConfigureWebTestClient
class ApiKeyAuthWebFilterIT {

    private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> SSE_MAP =
            new ParameterizedTypeReference<>() {
            };

    private final WebTestClient webTestClient;

    @Autowired
    ApiKeyAuthWebFilterIT(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    @Test
    void actuatorShouldNotRequireAuth() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void stream_shouldReturn401WithoutAuthorization() {
        ChatStreamRequest req = new ChatStreamRequest(
                null,
                "mock",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                2000,
                null
        );

        webTestClient.post()
                .uri("/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(Map.class)
                .value(body -> {
                    Assertions.assertTrue(body.containsKey("error"));
                    Object errorObj = body.get("error");
                    Assertions.assertInstanceOf(Map.class, errorObj);
                    Map<?, ?> error = (Map<?, ?>) errorObj;
                    Assertions.assertEquals("UNAUTHORIZED", error.get("code"));
                    Assertions.assertNotNull(error.get("requestId"));
                });
    }

    @Test
    void stream_shouldAllowWithValidApiKey() {
        ChatStreamRequest req = new ChatStreamRequest(
                "req_auth_ok",
                "mock",
                List.of(new ChatMessage("user", "hi")),
                0.2,
                16,
                2000,
                null
        );

        FluxExchangeResult<ServerSentEvent<Map<String, Object>>> result = webTestClient.post()
                .uri("/v1/chat/stream")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test_key")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_MAP);

        StepVerifier.create(result.getResponseBody())
                .assertNext(e -> Assertions.assertEquals("meta", e.event()))
                .verifyComplete();
    }
}
