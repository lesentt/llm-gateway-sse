package org.example.upstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.dto.ChatMessage;
import org.example.api.dto.ChatStreamRequest;
import org.example.config.GatewayUpstreamProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

class OpenAiUpstreamChatClientTest {

    @Test
    void resolveModelShouldFallbackToConfiguredDefault() {
        OpenAiUpstreamChatClient client = createClient("gpt-4o-mini");
        ChatStreamRequest request = new ChatStreamRequest(
                null,
                null,
                List.of(new ChatMessage("user", "hello")),
                null,
                null,
                null,
                null
        );

        Assertions.assertEquals("gpt-4o-mini", client.resolveModel(request));
    }

    @Test
    void extractDeltaContentShouldReturnTextToken() {
        OpenAiUpstreamChatClient client = createClient("gpt-4o-mini");
        String payload = """
                {"choices":[{"delta":{"content":"你好"}}]}
                """;

        Assertions.assertEquals("你好", client.extractDeltaContent(payload));
    }

    @Test
    void isDoneEventShouldMatchDoneMarker() {
        OpenAiUpstreamChatClient client = createClient("gpt-4o-mini");
        Assertions.assertTrue(client.isDoneEvent("[DONE]"));
        Assertions.assertFalse(client.isDoneEvent("{\"choices\":[]}"));
    }

    private static OpenAiUpstreamChatClient createClient(String defaultModel) {
        GatewayUpstreamProperties properties = new GatewayUpstreamProperties();
        properties.getOpenai().setDefaultModel(defaultModel);
        properties.getOpenai().setApiKey("test-key");
        return new OpenAiUpstreamChatClient(WebClient.builder(), new ObjectMapper(), properties);
    }
}
