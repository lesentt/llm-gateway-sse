package org.example.ratelimit;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class RedisRateLimitStore implements RateLimitStore {

    private static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            """
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return c
            """,
            Long.class
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisRateLimitStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Long> increment(String key, Duration ttl) {
        long seconds = Math.max(1, ttl.getSeconds());
        return redisTemplate.execute(INCR_EXPIRE_SCRIPT, List.of(key), String.valueOf(seconds))
                .single();
    }
}

