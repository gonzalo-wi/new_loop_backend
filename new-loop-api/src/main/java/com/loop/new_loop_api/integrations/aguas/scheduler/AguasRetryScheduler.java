package com.loop.new_loop_api.integrations.aguas.scheduler;

import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.common.service.IntegrationRetryDispatcher;
import lombok.RequiredArgsConstructor;
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
    @Scheduled(fixedDelayString = "${integrations.aguas.retry-interval-ms}")
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
