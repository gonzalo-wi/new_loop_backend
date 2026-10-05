package com.loop.new_loop_api.integrations.common.scheduler;

import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Keeps integration_logs from growing forever. Only finished attempts are purged: ERROR/PENDING/RETRYING
 * rows are what an operator still has to act on, so they stay until they are resolved.
 */
@Component
@RequiredArgsConstructor
public class IntegrationLogPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(IntegrationLogPurgeScheduler.class);

    private static final Set<IntegrationStatus> PURGEABLE_STATUSES = Set.of(IntegrationStatus.SENT, IntegrationStatus.CANCELLED);

    private final IntegrationLogRepository integrationLogRepository;

    @Value("${app.integration-logs.retention-days}")
    private int retentionDays;

    // 00:30, so it doesn't overlap the audit purge at midnight.
    @Scheduled(cron = "0 30 0 * * *")
    @SchedulerLock(name = "integrationLogPurgeScheduler.purgeFinishedLogs", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void purgeFinishedLogs() {
        // created_at, not sent_at: CANCELLED logs never get a sent_at, so they would never be purged.
        var deleted = integrationLogRepository.deleteByStatusInAndCreatedAtBefore(
                PURGEABLE_STATUSES, LocalDateTime.now().minusDays(retentionDays));
        if (deleted > 0) {
            log.info("Purged {} finished integration log(s) older than {} days", deleted, retentionDays);
        } else {
            log.debug("No finished integration logs older than {} days to purge", retentionDays);
        }
    }
}
