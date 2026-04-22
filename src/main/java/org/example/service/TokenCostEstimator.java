package org.example.service;

import org.example.config.GatewayM4Properties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class TokenCostEstimator {

    private final GatewayM4Properties gatewayM4Properties;

    public TokenCostEstimator(GatewayM4Properties gatewayM4Properties) {
        this.gatewayM4Properties = gatewayM4Properties;
    }

    public UsageEstimate estimate(int totalChars) {
        GatewayM4Properties.CostEstimation config = gatewayM4Properties.getCostEstimation();
        int charsPerToken = Math.max(1, config.getCharsPerToken());
        int tokenEstimated = Math.max(1, totalChars / charsPerToken);
        BigDecimal costEstimated = config.getUsdPer1kTokens()
                .multiply(BigDecimal.valueOf(tokenEstimated))
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        return new UsageEstimate(tokenEstimated, costEstimated, config.getAlgorithmVersion());
    }
}
