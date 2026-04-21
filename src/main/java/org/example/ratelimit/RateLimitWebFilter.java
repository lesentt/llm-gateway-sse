package org.example.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.api.error.ErrorResponse;
import org.example.api.error.GatewayErrorCode;
import org.example.config.GatewayRateLimitProperties;
import org.example.web.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Component
@Order(20)
public class RateLimitWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitWebFilter.class);

    public static final String API_KEY_HASH_ATTRIBUTE = "apiKeyHash";

    private final GatewayRateLimitProperties rateLimitProperties;
    private final RateLimitStore rateLimitStore;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public RateLimitWebFilter(
            GatewayRateLimitProperties rateLimitProperties,
            RateLimitStore rateLimitStore,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.rateLimitProperties = rateLimitProperties;
        this.rateLimitStore = rateLimitStore;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (!requiresRateLimit(rawPath)) {
            return chain.filter(exchange);
        }

        String requestId = ensureRequestId(exchange);
        String subject = extractSubject(exchange);
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = "rl:" + subject + ":" + epochMinute;

        Duration ttl = Duration.ofSeconds(Math.max(1, rateLimitProperties.getWindowSeconds()));
        int rpm = Math.max(1, rateLimitProperties.getRpm());

        return rateLimitStore.increment(key, ttl)
                .flatMap(current -> {
                    if (current <= rpm) {
                        return chain.filter(exchange);
                    }
                    log.info("requestId={} rateLimited=true subject={} current={} limit={}", requestId, subject, current, rpm);
                    recordRateLimited();
                    return tooManyRequests(exchange, requestId);
                });
    }

    private static boolean requiresRateLimit(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        if (rawPath.startsWith("/actuator")) {
            return false;
        }
        return rawPath.startsWith("/v1/");
    }

    private static String extractSubject(ServerWebExchange exchange) {
        Object apiKeyHash = exchange.getAttribute(API_KEY_HASH_ATTRIBUTE);
        if (apiKeyHash instanceof String s && StringUtils.hasText(s)) {
            return "ak_" + s;
        }
        return "global";
    }

    private static String ensureRequestId(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(RequestId.ATTRIBUTE_NAME);
        if (attr instanceof String s && StringUtils.hasText(s)) {
            return s;
        }
        String requestId = RequestId.generate();
        exchange.getAttributes().put(RequestId.ATTRIBUTE_NAME, requestId);
        return requestId;
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String requestId) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse body = ErrorResponse.of(GatewayErrorCode.RATE_LIMITED, "Rate limited, please retry.", requestId);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Rate limited\"}}".getBytes(StandardCharsets.UTF_8);
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private void recordRateLimited() {
        Counter.builder("gateway_rate_limited_total")
                .description("Rate limited requests total")
                .register(meterRegistry)
                .increment();
    }
}
