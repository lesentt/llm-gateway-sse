package org.example.ratelimit;

import reactor.core.publisher.Mono;

import java.time.Duration;

public interface RateLimitStore {

    /**
     * Increments a counter for the given key and ensures it expires after ttl.
     *
     * @return the current counter value after increment
     */
    Mono<Long> increment(String key, Duration ttl);
}

