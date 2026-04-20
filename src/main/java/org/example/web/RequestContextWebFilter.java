package org.example.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class RequestContextWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestContextWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();

        String headerRequestId = exchange.getRequest().getHeaders().getFirst(RequestId.HEADER_NAME);
        String requestId = StringUtils.hasText(headerRequestId) ? headerRequestId : RequestId.generate();
        exchange.getAttributes().put(RequestId.ATTRIBUTE_NAME, requestId);

        exchange.getResponse().beforeCommit(() -> {
            Object currentRequestId = exchange.getAttribute(RequestId.ATTRIBUTE_NAME);
            if (currentRequestId != null && !exchange.getResponse().getHeaders().containsKey(RequestId.HEADER_NAME)) {
                exchange.getResponse().getHeaders().set(RequestId.HEADER_NAME, String.valueOf(currentRequestId));
            }
            return Mono.empty();
        });

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    Duration latency = Duration.ofNanos(System.nanoTime() - startNanos);
                    Object finalRequestId = exchange.getAttribute(RequestId.ATTRIBUTE_NAME);
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int rawStatus = statusCode != null ? statusCode.value() : -1;

                    log.info("requestId={} method={} path={} status={} latencyMs={} signal={}",
                            finalRequestId,
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getURI().getRawPath(),
                            rawStatus,
                            latency.toMillis(),
                            signalType);
                });
    }
}

