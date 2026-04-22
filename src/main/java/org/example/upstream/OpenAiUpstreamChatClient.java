package org.example.upstream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.dto.ChatMessage;
import org.example.api.dto.ChatStreamRequest;
import org.example.config.GatewayUpstreamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "llm-gateway.upstream", name = "mode", havingValue = "openai")
public class OpenAiUpstreamChatClient implements UpstreamChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiUpstreamChatClient.class);
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GatewayUpstreamProperties properties;

    public OpenAiUpstreamChatClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            GatewayUpstreamProperties properties
    ) {
        this.webClient = webClientBuilder.baseUrl(properties.getOpenai().getBaseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Flux<String> stream(ChatStreamRequest request, String requestId) {
        String apiKey = properties.getOpenai().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            return Flux.error(new IllegalStateException("Missing llm-gateway.upstream.openai.api-key"));
        }
        String model = resolveModel(request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("stream", true);
        payload.put("messages", mapMessages(request.messages()));
        if (request.temperature() != null) {
            payload.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            payload.put("max_tokens", request.maxTokens());
        }

        return webClient.post()
                .uri(properties.getOpenai().getChatCompletionsPath())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(SSE_STRING)
                .mapNotNull(ServerSentEvent::data)
                .takeUntil(this::isDoneEvent)
                .filter(data -> !isDoneEvent(data))
                .map(this::extractDeltaContent)
                .filter(StringUtils::hasLength)
                .doOnSubscribe(s -> log.info("requestId={} openaiUpstream=subscribed model={}", requestId, model))
                .doOnCancel(() -> log.info("requestId={} openaiUpstream=cancelled model={}", requestId, model))
                .doFinally(signalType -> log.info("requestId={} openaiUpstream=terminated model={} signal={}",
                        requestId, model, signalType));
    }

    @Override
    public String resolveModel(ChatStreamRequest request) {
        if (StringUtils.hasText(request.model())) {
            return request.model();
        }
        return properties.getOpenai().getDefaultModel();
    }

    boolean isDoneEvent(String data) {
        return "[DONE]".equals(data != null ? data.trim() : null);
    }

    private List<Map<String, String>> mapMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

    String extractDeltaContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            JsonNode contentNode = choices.get(0).path("delta").path("content");
            return contentNode.isTextual() ? contentNode.asText() : "";
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid upstream stream payload", ex);
        }
    }
}
