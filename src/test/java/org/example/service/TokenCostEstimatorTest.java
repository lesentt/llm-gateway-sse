package org.example.service;

import org.example.config.GatewayM4Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class TokenCostEstimatorTest {

    @Test
    void estimateShouldUseConfiguredFormula() {
        GatewayM4Properties properties = new GatewayM4Properties();
        properties.getCostEstimation().setCharsPerToken(4);
        properties.getCostEstimation().setUsdPer1kTokens(new BigDecimal("0.001"));
        properties.getCostEstimation().setAlgorithmVersion("v1_chars_div_4");
        TokenCostEstimator estimator = new TokenCostEstimator(properties);

        UsageEstimate estimate = estimator.estimate(40);

        Assertions.assertEquals(10, estimate.tokenEstimated());
        Assertions.assertEquals(new BigDecimal("0.00001000"), estimate.costEstimated());
        Assertions.assertEquals("v1_chars_div_4", estimate.algorithmVersion());
    }

    @Test
    void estimateShouldGuaranteeMinimumOneToken() {
        GatewayM4Properties properties = new GatewayM4Properties();
        TokenCostEstimator estimator = new TokenCostEstimator(properties);

        UsageEstimate estimate = estimator.estimate(0);

        Assertions.assertEquals(1, estimate.tokenEstimated());
        Assertions.assertTrue(estimate.costEstimated().compareTo(BigDecimal.ZERO) > 0);
    }
}
