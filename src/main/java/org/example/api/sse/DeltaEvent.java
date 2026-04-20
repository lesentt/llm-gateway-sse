package org.example.api.sse;


public record DeltaEvent(
        int index,
        String delta
) {
}

