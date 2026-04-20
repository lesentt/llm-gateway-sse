package org.example.web;

import java.util.UUID;

public final class RequestId {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = "requestId";

    private RequestId() {
    }

    public static String generate() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}

