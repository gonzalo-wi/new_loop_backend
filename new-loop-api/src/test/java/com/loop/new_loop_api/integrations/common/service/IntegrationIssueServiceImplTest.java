package com.loop.new_loop_api.integrations.common.service;

import com.loop.new_loop_api.integrations.common.dto.IntegrationIssueKind;
import com.loop.new_loop_api.integrations.common.dto.IntegrationIssuesResponse.Issue;
import com.loop.new_loop_api.integrations.common.repository.IntegrationIssueRepository;
import com.loop.new_loop_api.integrations.common.repository.IntegrationIssueRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationIssueServiceImplTest {

    private static final int MAX_RETRIES = 5;

    @Mock private IntegrationIssueRepository repository;

    private IntegrationIssueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IntegrationIssueServiceImpl(repository);
        ReflectionTestUtils.setField(service, "maxRetries", MAX_RETRIES);
        ReflectionTestUtils.setField(service, "stuckAfterMinutes", 30);
        ReflectionTestUtils.setField(service, "neverSentLookbackDays", 30);
    }

    @Test
    void shouldClassifyEachRowByWhatTheOperatorHasToDo() {
        when(repository.findLogIssues(any())).thenReturn(List.of(
                logRow("ERROR", 2, 10),
                logRow("ERROR", MAX_RETRIES, 20),
                logRow("PENDING", 0, 40)));
        when(repository.findNeverSent(any(), any())).thenReturn(List.of(neverSentRow(50)));

        var issues = service.getIssues().issues();

        assertThat(issues).extracting(Issue::kind).containsExactly(
                IntegrationIssueKind.RETRIES_EXHAUSTED,
                IntegrationIssueKind.STUCK,
                IntegrationIssueKind.NEVER_SENT,
                IntegrationIssueKind.RETRYING);
    }

    @Test
    void shouldCountEachKindInTheSummary() {
        when(repository.findLogIssues(any())).thenReturn(List.of(
                logRow("ERROR", 1, 10),
                logRow("ERROR", 3, 10),
                logRow("ERROR", MAX_RETRIES, 10),
                logRow("RETRYING", 1, 60)));
        when(repository.findNeverSent(any(), any())).thenReturn(List.of());

        var summary = service.getIssues().summary();

        assertThat(summary.retrying()).isEqualTo(2);
        assertThat(summary.retriesExhausted()).isEqualTo(1);
        assertThat(summary.stuck()).isEqualTo(1);
        assertThat(summary.neverSent()).isZero();
    }

    @Test
    void shouldListOldestFirstWithinTheSameKind() {
        var older = logRow("ERROR", MAX_RETRIES, 120);
        var newer = logRow("ERROR", MAX_RETRIES, 10);
        when(repository.findLogIssues(any())).thenReturn(List.of(newer, older));
        when(repository.findNeverSent(any(), any())).thenReturn(List.of());

        var issues = service.getIssues().issues();

        assertThat(issues).extracting(Issue::logId).containsExactly(older.logId(), newer.logId());
    }

    @Test
    void shouldUseTheConfiguredThresholds() {
        when(repository.findLogIssues(any())).thenReturn(List.of());
        when(repository.findNeverSent(any(), any())).thenReturn(List.of());
        var stuckBefore = LocalDateTime.now().minusMinutes(30);
        var lookbackFrom = LocalDateTime.now().minusDays(30);

        service.getIssues();

        var stuck    = ArgumentCaptor.forClass(LocalDateTime.class);
        var lookback = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findNeverSent(stuck.capture(), lookback.capture());
        assertThat(stuck.getValue()).isBetween(stuckBefore, LocalDateTime.now().minusMinutes(30));
        assertThat(lookback.getValue()).isBetween(lookbackFrom, LocalDateTime.now().minusDays(30));
    }

    private static IntegrationIssueRow logRow(String status, int retryCount, int minutesAgo) {
        return logRow("EQUIPMENT_OUT", status, retryCount, minutesAgo);
    }

    private static IntegrationIssueRow logRow(String operation, String status, int retryCount, int minutesAgo) {
        return new IntegrationIssueRow(UUID.randomUUID(), "AGUAS", operation, "DispenserMovement", UUID.randomUUID(),
                status, retryCount, "HTTP 500", LocalDateTime.now().minusMinutes(minutesAgo), "Rto 1", "LOAD", LocalDate.now());
    }

    @Test
    void shouldReportAFailedAguasCancellationAsNeedingManualAction() {
        // The retry handlers can't redo a cancellation, so it would otherwise sit at "retrying 0/5" forever.
        when(repository.findLogIssues(any())).thenReturn(List.of(logRow("EQUIPMENT_DELETE", "ERROR", 0, 10)));
        when(repository.findNeverSent(any(), any())).thenReturn(List.of());

        var issues = service.getIssues().issues();

        assertThat(issues).extracting(Issue::kind).containsExactly(IntegrationIssueKind.RETRIES_EXHAUSTED);
    }

    @Test
    void shouldFlagTheListAsTruncatedWhenAQueryHitsItsLimit() {
        var full = java.util.stream.IntStream.range(0, IntegrationIssueRepository.MAX_ISSUES)
                .mapToObj(i -> logRow("ERROR", 1, 10)).toList();
        when(repository.findLogIssues(any())).thenReturn(full);
        when(repository.findNeverSent(any(), any())).thenReturn(List.of());

        assertThat(service.getIssues().truncated()).isTrue();
    }

    @Test
    void shouldNotFlagTheListAsTruncatedBelowTheLimit() {
        when(repository.findLogIssues(any())).thenReturn(List.of(logRow("ERROR", 1, 10)));
        when(repository.findNeverSent(any(), any())).thenReturn(List.of());

        assertThat(service.getIssues().truncated()).isFalse();
    }

    private static IntegrationIssueRow neverSentRow(int minutesAgo) {
        return new IntegrationIssueRow(null, "AGUAS", null, "StockControl", UUID.randomUUID(),
                null, 0, null, LocalDateTime.now().minusMinutes(minutesAgo), "Rto 2", "EXIT", LocalDate.now());
    }
}
