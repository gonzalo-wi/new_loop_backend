package com.loop.new_loop_api.integrations.aguas.scheduler;

import com.loop.new_loop_api.integrations.aguas.service.AguasRetryDispatcher;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
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

    private final IntegrationLogRepository integrationLogRepository;
    private final AguasRetryDispatcher     aguasRetryDispatcher;

    @Value("${integrations.aguas.max-retries}")
    private int maxRetries;

    /** Periodically re-sends failed Aguas attempts that have not exhausted their retries. */
    @Scheduled(fixedDelayString = "${integrations.aguas.retry-interval-ms}")
    public void retryFailed() {
        var failed = integrationLogRepository.findByIntegrationNameAndStatusAndRetryCountLessThan(
                IntegrationName.AGUAS, IntegrationStatus.ERROR, maxRetries);

        if (failed.isEmpty()) return;

        log.info("Retrying {} failed Aguas integration(s)", failed.size());
        failed.forEach(integrationLog -> {
            try {
                aguasRetryDispatcher.retry(integrationLog.getId());
            } catch (Exception e) {
                log.error("Unexpected error retrying Aguas log {}: {}", integrationLog.getId(), e.getMessage());
            }
        });
    }
}
