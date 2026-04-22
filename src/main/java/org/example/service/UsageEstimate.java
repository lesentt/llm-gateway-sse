package org.example.service;

import java.math.BigDecimal;

public record UsageEstimate(
        int tokenEstimated,
        BigDecimal costEstimated,
        String algorithmVersion
) {
}
