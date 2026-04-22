package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "llm-gateway.postgres")
public class GatewayPostgresProperties {

    private String host = "localhost";
    private int port = 5432;
    private String database = "llm_gateway";
    private String username = "llm_gateway";
    private String password = "llm_gateway";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String jdbcUrl(int connectTimeoutSeconds) {
        int timeout = Math.max(1, connectTimeoutSeconds);
        return "jdbc:postgresql://%s:%d/%s?connectTimeout=%d&socketTimeout=%d"
                .formatted(host, port, database, timeout, timeout);
    }
}
