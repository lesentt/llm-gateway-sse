package org.example.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.error.ErrorResponse;
import org.example.api.error.GatewayErrorCode;
import org.example.config.GatewaySecurityProperties;
import org.example.ratelimit.RateLimitWebFilter;
import org.example.web.RequestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
@Order(10)
public class ApiKeyAuthWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthWebFilter.class);

    private final GatewaySecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthWebFilter(GatewaySecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!securityProperties.getApiKey().isEnabled()) {
            return chain.filter(exchange);
        }

        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getRawPath();
        if (!requiresAuth(path)) {
            return chain.filter(exchange);
        }

        Object attr = exchange.getAttribute(RequestId.ATTRIBUTE_NAME);
        String requestId = attr instanceof String s ? s : RequestId.generate();
        exchange.getAttributes().put(RequestId.ATTRIBUTE_NAME, requestId);
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String apiKey = extractBearerToken(authorization);

        if (!StringUtils.hasText(apiKey)) {
            return unauthorized(exchange, requestId);
        }

        List<String> allowedHashes = securityProperties.getApiKey().getHashes();
        if (allowedHashes == null || allowedHashes.isEmpty()) {
            log.warn("requestId={} apiKeyAuthEnabled=true but no hashes configured", requestId);
            return unauthorized(exchange, requestId);
        }

        String expectedHash = sha256Hex(securityProperties.getApiKey().getPepper(), apiKey);
        boolean ok = allowedHashes.stream().anyMatch(h -> safeEquals(h, expectedHash));
        if (!ok) {
            return unauthorized(exchange, requestId);
        }

        exchange.getAttributes().put(RateLimitWebFilter.API_KEY_HASH_ATTRIBUTE, expectedHash);
        return chain.filter(exchange);
    }

    private static boolean requiresAuth(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        if (rawPath.startsWith("/actuator")) {
            return false;
        }
        return rawPath.startsWith("/v1/");
    }

    private static String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (!authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String requestId) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse body = ErrorResponse.of(GatewayErrorCode.UNAUTHORIZED, "Unauthorized", requestId);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Unauthorized\"}}".getBytes(StandardCharsets.UTF_8);
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private static String sha256Hex(String pepper, String apiKey) {
        String content = (pepper != null ? pepper : "") + ":" + apiKey;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
