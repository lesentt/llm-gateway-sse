package org.example.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GatewayPostgresPropertiesTest {

    @Test
    void jdbcUrlShouldContainConnectionAndSocketTimeout() {
        GatewayPostgresProperties properties = new GatewayPostgresProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(5432);
        properties.setDatabase("llm_gateway");

        String jdbcUrl = properties.jdbcUrl(3);

        Assertions.assertTrue(jdbcUrl.contains("jdbc:postgresql://127.0.0.1:5432/llm_gateway"));
        Assertions.assertTrue(jdbcUrl.contains("connectTimeout=3"));
        Assertions.assertTrue(jdbcUrl.contains("socketTimeout=3"));
    }

    @Test
    void jdbcUrlShouldClampTimeoutToAtLeastOneSecond() {
        GatewayPostgresProperties properties = new GatewayPostgresProperties();

        String jdbcUrl = properties.jdbcUrl(0);

        Assertions.assertTrue(jdbcUrl.contains("connectTimeout=1"));
        Assertions.assertTrue(jdbcUrl.contains("socketTimeout=1"));
    }
}
