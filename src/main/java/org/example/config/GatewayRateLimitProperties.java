package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "llm-gateway.ratelimit")
public class GatewayRateLimitProperties {

    private boolean enabled = false;

    /**
     * Requests per minute.
     */
    private int rpm = 100;

    /**
     * Expiration seconds for the counter key (should be slightly greater than 60s).
     */
    private int windowSeconds = 65;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRpm() {
        return rpm;
    }

    public void setRpm(int rpm) {
        this.rpm = rpm;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}

