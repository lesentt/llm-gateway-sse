package org.example.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final MeterRegistry meterRegistry;

    public ApiKeyAuthWebFilter(GatewaySecurityProperties securityProperties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        //如果API Key认证未启用，则直接放行
        if (!securityProperties.getApiKey().isEnabled()) {
            return chain.filter(exchange);
        }

        //如果请求方法为OPTIONS，则直接放行
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        //如果请求路径不需要认证，则直接放行
        String path = exchange.getRequest().getURI().getRawPath();
        if (!requiresAuth(path)) {
            return chain.filter(exchange);
        }

        //生成请求ID
        Object attr = exchange.getAttribute(RequestId.ATTRIBUTE_NAME);
        String requestId = attr instanceof String s ? s : RequestId.generate();
        exchange.getAttributes().put(RequestId.ATTRIBUTE_NAME, requestId);
        //提取Authorization头中的Bearer Token
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String apiKey = extractBearerToken(authorization);

        //如果Bearer Token为空，则返回未授权响应
        if (!StringUtils.hasText(apiKey)) {
            recordAuthFailure();
            return unauthorized(exchange, requestId);
        }
        //获取允许的哈希列表
        List<String> allowedHashes = securityProperties.getApiKey().getHashes();
        //如果允许的哈希列表为空，则返回未授权响应
        if (allowedHashes == null || allowedHashes.isEmpty()) {
            log.warn("requestId={} apiKeyAuthEnabled=true but no hashes configured", requestId);
            return unauthorized(exchange, requestId);
        }

        //计算期望的哈希值
        String expectedHash = sha256Hex(securityProperties.getApiKey().getPepper(), apiKey);
        //如果期望的哈希值在允许的哈希列表中，则放行
        boolean ok = allowedHashes.stream().anyMatch(h -> safeEquals(h, expectedHash));
        //如果期望的哈希值不在允许的哈希列表中，则返回未授权响应
        if (!ok) {
            recordAuthFailure();
            return unauthorized(exchange, requestId);
        }

        exchange.getAttributes().put(RateLimitWebFilter.API_KEY_HASH_ATTRIBUTE, expectedHash);
        return chain.filter(exchange);
    }

    //判断路径是否需要认证
    private static boolean requiresAuth(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        if (rawPath.startsWith("/actuator")) {
            return false;
        }
        return rawPath.startsWith("/v1/");
    }

    //提取Bearer Token
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

    //未授权响应
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

    private void recordAuthFailure() {
        Counter.builder("gateway_auth_failures_total")
                .description("Authorization failures total")
                .register(meterRegistry)
                .increment();
    }

    //SHA-256加密
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

    //安全比较字符串
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
