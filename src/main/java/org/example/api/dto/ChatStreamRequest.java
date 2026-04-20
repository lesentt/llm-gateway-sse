package org.example.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatStreamRequest(
        String requestId,
        String model,
        @NotEmpty @Size(max = 64) List<@Valid ChatMessage> messages,
        @Min(0) @Max(2) Double temperature,
        @Min(1) @Max(8192) Integer maxTokens,
        @Min(1) @Max(120_000) Integer timeoutMs,
        @Valid RetryConfig retry
) {
}

