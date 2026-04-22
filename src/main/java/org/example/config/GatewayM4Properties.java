package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "llm-gateway.m4")
public class GatewayM4Properties {

    private Persistence persistence = new Persistence();
    private Events events = new Events();
    private CostEstimation costEstimation = new CostEstimation();

    public Persistence getPersistence() {
        return persistence;
    }

    public void setPersistence(Persistence persistence) {
        this.persistence = persistence;
    }

    public Events getEvents() {
        return events;
    }

    public void setEvents(Events events) {
        this.events = events;
    }

    public CostEstimation getCostEstimation() {
        return costEstimation;
    }

    public void setCostEstimation(CostEstimation costEstimation) {
        this.costEstimation = costEstimation;
    }

    public static class Persistence {
        private boolean enabled = true;
        private int connectTimeoutSeconds = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }
    }

    public static class Events {
        private boolean enabled = true;
        private String exchange = "llm.gateway.events";
        private String routingKey = "request.completed";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }
    }

    public static class CostEstimation {
        private int charsPerToken = 4;
        private BigDecimal usdPer1kTokens = new BigDecimal("0.001");
        private String algorithmVersion = "v1_chars_div_4";

        public int getCharsPerToken() {
            return charsPerToken;
        }

        public void setCharsPerToken(int charsPerToken) {
            this.charsPerToken = charsPerToken;
        }

        public BigDecimal getUsdPer1kTokens() {
            return usdPer1kTokens;
        }

        public void setUsdPer1kTokens(BigDecimal usdPer1kTokens) {
            this.usdPer1kTokens = usdPer1kTokens;
        }

        public String getAlgorithmVersion() {
            return algorithmVersion;
        }

        public void setAlgorithmVersion(String algorithmVersion) {
            this.algorithmVersion = algorithmVersion;
        }
    }
}
