package org.example.api;

import jakarta.validation.Valid;
import org.example.api.dto.ChatStreamRequest;
import org.example.service.ChatStreamService;
import org.example.web.RequestId;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

@RestController
@Validated
public class ChatStreamController {

    private final ChatStreamService chatStreamService;

    public ChatStreamController(ChatStreamService chatStreamService) {
        this.chatStreamService = chatStreamService;
    }

    @PostMapping(value = "/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> stream(
            @Valid @RequestBody ChatStreamRequest request,
            ServerWebExchange exchange
    ) {
        // getAttribute<T> + String.valueOf overload resolution can wrongly infer T as char[] (bogus checkcast).
        Object attr = exchange.getAttribute(RequestId.ATTRIBUTE_NAME);
        String requestId = attr instanceof String s ? s : RequestId.generate();
        if (StringUtils.hasText(request.requestId())) {
            requestId = request.requestId();
            exchange.getAttributes().put(RequestId.ATTRIBUTE_NAME, requestId);
        }
        return chatStreamService.stream(request, requestId);
    }
}

