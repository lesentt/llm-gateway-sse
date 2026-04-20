package org.example.api.error;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GatewayError(
        @NotNull GatewayErrorCode code,
        @NotBlank String message,
        @NotBlank String requestId
) {
}

