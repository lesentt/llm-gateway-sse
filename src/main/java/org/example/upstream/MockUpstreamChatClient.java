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
        String model = request.model() != null ? request.model() : "mock";
        Flux<String> flux = switch (model) {
            case "mock_error" -> Flux.concat(
                    Flux.just("Hello", " "),
                    Flux.error(new RuntimeException("mock upstream error"))
            ).delayElements(Duration.ofMillis(10));
            case "mock_slow" -> Flux.just("Hello", " ", "from", " ", "mock", " ", "upstream", "!")
                    .delayElements(Duration.ofMillis(500));
            default -> Flux.just("Hello", " ", "from", " ", "mock", " ", "upstream", "!")
                    .delayElements(Duration.ofMillis(10));
        };

        return flux
                .doOnSubscribe(s -> log.info("requestId={} mockUpstream=subscribed model={}", requestId, model))
                .doOnCancel(() -> log.info("requestId={} mockUpstream=cancelled model={}", requestId, model))
                .doFinally(signalType -> log.info("requestId={} mockUpstream=terminated model={} signal={}",
                        requestId, model, signalType));
    }
}
