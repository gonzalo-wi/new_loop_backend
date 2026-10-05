package com.loop.new_loop_api.integrations.common.scheduler;

import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationLogPurgeSchedulerTest {

    @Mock private IntegrationLogRepository integrationLogRepository;

    private IntegrationLogPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new IntegrationLogPurgeScheduler(integrationLogRepository);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 90);
    }

    @Test
    void shouldPurgeOnlyFinishedLogsOlderThanRetention() {
        when(integrationLogRepository.deleteByStatusInAndCreatedAtBefore(any(), any())).thenReturn(3L);
        var before = LocalDateTime.now().minusDays(90);

        scheduler.purgeFinishedLogs();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<IntegrationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        var cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(integrationLogRepository).deleteByStatusInAndCreatedAtBefore(statuses.capture(), cutoff.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(IntegrationStatus.SENT, IntegrationStatus.CANCELLED);
        assertThat(cutoff.getValue()).isBetween(before, LocalDateTime.now().minusDays(90));
    }
}
