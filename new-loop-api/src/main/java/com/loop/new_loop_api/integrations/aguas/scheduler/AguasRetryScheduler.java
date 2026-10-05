package com.loop.new_loop_api.integrations.aguas.scheduler;

import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.common.service.IntegrationRetryDispatcher;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AguasRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AguasRetryScheduler.class);

    private final IntegrationLogRepository     integrationLogRepository;
    private final IntegrationRetryDispatcher   integrationRetryDispatcher;

    @Value("${integrations.aguas.max-retries}")
    private int maxRetries;

    /** Periodically re-sends failed integration attempts (Aguas + Odoo) that still have retries left. */
    @Scheduled(initialDelayString = "${integrations.aguas.retry-initial-delay-ms}",
               fixedDelayString   = "${integrations.aguas.retry-interval-ms}")
    // lockAtMostFor covers a full pass of slow/timing-out retries; lockAtLeastFor absorbs clock skew between instances.
    @SchedulerLock(name = "aguasRetryScheduler.retryFailed", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void retryFailed() {
        var failed = integrationLogRepository.findByStatusAndRetryCountLessThan(
                IntegrationStatus.ERROR, maxRetries);

        if (failed.isEmpty()) return;

        log.info("Retrying {} failed integration(s)", failed.size());
        failed.forEach(integrationLog -> {
            try {
                integrationRetryDispatcher.retry(integrationLog.getId());
            } catch (Exception e) {
                log.error("Unexpected error retrying integration log {}: {}", integrationLog.getId(), e.getMessage());
            }
        });
    }
}
