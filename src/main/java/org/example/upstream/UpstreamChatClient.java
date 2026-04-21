package org.example.upstream;

import org.example.api.dto.ChatStreamRequest;
import reactor.core.publisher.Flux;

public interface UpstreamChatClient {

    Flux<String> stream(ChatStreamRequest request, String requestId);
}

