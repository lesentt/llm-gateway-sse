package org.example.api.sse;

import org.example.api.error.ErrorResponse;

public record ErrorEvent(
        ErrorResponse error
) {
}

