package org.example.service;

import org.example.config.GatewayM4Properties;
import org.example.config.GatewayPostgresProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class JdbcRequestRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcRequestRecordRepository.class);

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS gateway_request_records (
                request_id VARCHAR(128) PRIMARY KEY,
                model VARCHAR(128) NOT NULL,
                status VARCHAR(32) NOT NULL,
                latency_ms BIGINT NOT NULL,
                token_estimated INTEGER NOT NULL,
                cost_estimated NUMERIC(18,8) NOT NULL,
                cost_algorithm_version VARCHAR(64) NOT NULL,
                error_code VARCHAR(64),
                error_message TEXT,
                client_aborted BOOLEAN NOT NULL,
                attempts INTEGER NOT NULL,
                timeout_ms INTEGER NOT NULL,
                started_at TIMESTAMPTZ NOT NULL,
                ended_at TIMESTAMPTZ NOT NULL,
                trace_id VARCHAR(64),
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO gateway_request_records (
                request_id, model, status, latency_ms, token_estimated, cost_estimated, cost_algorithm_version,
                error_code, error_message, client_aborted, attempts, timeout_ms, started_at, ended_at, trace_id, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (request_id) DO UPDATE SET
                model = EXCLUDED.model,
                status = EXCLUDED.status,
                latency_ms = EXCLUDED.latency_ms,
                token_estimated = EXCLUDED.token_estimated,
                cost_estimated = EXCLUDED.cost_estimated,
                cost_algorithm_version = EXCLUDED.cost_algorithm_version,
                error_code = EXCLUDED.error_code,
                error_message = EXCLUDED.error_message,
                client_aborted = EXCLUDED.client_aborted,
                attempts = EXCLUDED.attempts,
                timeout_ms = EXCLUDED.timeout_ms,
                started_at = EXCLUDED.started_at,
                ended_at = EXCLUDED.ended_at,
                trace_id = EXCLUDED.trace_id,
                updated_at = NOW()
            """;

    private final GatewayPostgresProperties postgresProperties;
    private final GatewayM4Properties gatewayM4Properties;
    private final AtomicBoolean tableReady = new AtomicBoolean(false);

    public JdbcRequestRecordRepository(
            GatewayPostgresProperties postgresProperties,
            GatewayM4Properties gatewayM4Properties
    ) {
        this.postgresProperties = postgresProperties;
        this.gatewayM4Properties = gatewayM4Properties;
    }

    public void save(StreamCompletionRecord record) {
        if (!gatewayM4Properties.getPersistence().isEnabled()) {
            return;
        }
        try (Connection connection = openConnection()) {
            ensureTable(connection);
            upsert(connection, record);
        } catch (SQLException ex) {
            log.warn("requestId={} persistRecordFailed reason={}", record.requestId(), ex.getMessage());
        }
    }

    private Connection openConnection() throws SQLException {
        String url = postgresProperties.jdbcUrl(gatewayM4Properties.getPersistence().getConnectTimeoutSeconds());
        return DriverManager.getConnection(url, postgresProperties.getUsername(), postgresProperties.getPassword());
    }

    private void ensureTable(Connection connection) throws SQLException {
        if (tableReady.get()) {
            return;
        }
        synchronized (tableReady) {
            if (tableReady.get()) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(CREATE_TABLE_SQL)) {
                statement.execute();
                tableReady.set(true);
            }
        }
    }

    private static void upsert(Connection connection, StreamCompletionRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, record.requestId());
            statement.setString(2, record.model());
            statement.setString(3, record.status());
            statement.setLong(4, record.latencyMs());
            statement.setInt(5, record.tokenEstimated());
            statement.setBigDecimal(6, record.costEstimated());
            statement.setString(7, record.costAlgorithmVersion());
            statement.setString(8, record.errorCode());
            statement.setString(9, record.errorMessage());
            statement.setBoolean(10, record.clientAborted());
            statement.setInt(11, record.attempts());
            statement.setInt(12, record.timeoutMs());
            statement.setObject(13, record.startedAt().atOffset(ZoneOffset.UTC));
            statement.setObject(14, record.endedAt().atOffset(ZoneOffset.UTC));
            if (record.traceId() == null) {
                statement.setNull(15, Types.VARCHAR);
            } else {
                statement.setString(15, record.traceId());
            }
            statement.executeUpdate();
        }
    }
}
