package org.example.upstream;

import org.example.api.dto.ChatStreamRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Component
public class MockUpstreamChatClient implements UpstreamChatClient {

    private static final Logger log = LoggerFactory.getLogger(MockUpstreamChatClient.class);

    @Override
    public Flux<String> stream(ChatStreamRequest request, String requestId) {
        return Flux.just("Hello", " ", "from", " ", "mock", " ", "upstream", "!")
                .delayElements(Duration.ofMillis(10))
                .doOnSubscribe(s -> log.info("requestId={} mockUpstream=subscribed", requestId))
                .doOnCancel(() -> log.info("requestId={} mockUpstream=cancelled", requestId));
    }
}

