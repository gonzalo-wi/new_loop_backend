package com.loop.new_loop_api.integrations.common.scheduler;

import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationLogPurgeIntegrationTest extends PostgresIntegrationTest {

    @Autowired private IntegrationLogPurgeScheduler scheduler;
    @Autowired private JdbcTemplate                 jdbc;

    @Test
    void shouldDeleteOnlyOldFinishedLogs() {
        // Logs of one fake entity, one per status, both older and newer than the 90-day default retention.
        var entityId = UUID.randomUUID();
        var old      = LocalDateTime.now().minusDays(120);
        var recent   = LocalDateTime.now().minusDays(10);
        for (var status : IntegrationStatus.values()) {
            insert(entityId, status, old);
            insert(entityId, status, recent);
        }

        scheduler.purgeFinishedLogs();

        var oldLeft = jdbc.queryForList(
                "select status from integration_logs where entity_id = ? and created_at < ?",
                String.class, entityId, Timestamp.valueOf(LocalDateTime.now().minusDays(90)));
        assertThat(oldLeft).containsExactlyInAnyOrder("PENDING", "ERROR", "RETRYING");
        assertThat(recentCount(entityId)).isEqualTo(IntegrationStatus.values().length);
    }

    private int recentCount(UUID entityId) {
        return jdbc.queryForObject(
                "select count(*) from integration_logs where entity_id = ? and created_at >= ?",
                Integer.class, entityId, Timestamp.valueOf(LocalDateTime.now().minusDays(90)));
    }

    private void insert(UUID entityId, IntegrationStatus status, LocalDateTime createdAt) {
        jdbc.update("""
                insert into integration_logs (integration_name, operation_type, entity_name, entity_id, status, created_at)
                values ('AGUAS', 'TEST', 'Test', ?, ?, ?)
                """, entityId, status.name(), Timestamp.valueOf(createdAt));
    }
}
