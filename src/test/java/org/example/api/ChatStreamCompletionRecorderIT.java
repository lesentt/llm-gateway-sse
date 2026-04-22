package org.example.api;

import org.example.api.dto.ChatMessage;
import org.example.api.dto.ChatStreamRequest;
import org.example.service.StreamCompletionRecord;
import org.example.service.StreamCompletionRecorder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class ChatStreamCompletionRecorderIT {

    private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> SSE_MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private StreamCompletionRecorder streamCompletionRecorder;

    @Test
    void streamShouldCallCompletionRecorderOnSuccess() {
        ChatStreamRequest request = new ChatStreamRequest(
                "req_it_record_ok",
                "mock",
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
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_MAP);

        StepVerifier.create(result.getResponseBody().collectList())
                .assertNext(events -> {
                    Assertions.assertTrue(events.size() >= 3);
                    Assertions.assertEquals("meta", events.getFirst().event());
                    Assertions.assertEquals("done", events.getLast().event());
                })
                .verifyComplete();

        ArgumentCaptor<StreamCompletionRecord> recordCaptor = ArgumentCaptor.forClass(StreamCompletionRecord.class);
        Mockito.verify(streamCompletionRecorder, Mockito.timeout(500).times(1)).record(recordCaptor.capture());

        StreamCompletionRecord record = recordCaptor.getValue();
        Assertions.assertEquals("req_it_record_ok", record.requestId());
        Assertions.assertEquals("ok", record.status());
        Assertions.assertFalse(record.clientAborted());
        Assertions.assertTrue(record.tokenEstimated() > 0);
    }
}
