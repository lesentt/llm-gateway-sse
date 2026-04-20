package org.example.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RetryConfig(
        @Min(0) @Max(5) int maxAttempts,
        @Min(0) int backoffMs
) {
}

