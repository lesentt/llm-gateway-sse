package org.example.api.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ErrorResponse(
        @NotNull @Valid GatewayError error
) {
    public static ErrorResponse of(GatewayErrorCode code, String message, String requestId) {
        return new ErrorResponse(new GatewayError(code, message, requestId));
    }
}

