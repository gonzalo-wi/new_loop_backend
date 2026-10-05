package com.loop.new_loop_api.integrations.common.service;

import com.loop.new_loop_api.integrations.common.dto.IntegrationIssueKind;
import com.loop.new_loop_api.integrations.common.dto.IntegrationIssuesResponse;
import com.loop.new_loop_api.integrations.common.dto.IntegrationIssuesResponse.Issue;
import com.loop.new_loop_api.integrations.common.dto.IntegrationIssuesResponse.Summary;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.repository.IntegrationIssueRepository;
import com.loop.new_loop_api.integrations.common.repository.IntegrationIssueRow;
import com.loop.new_loop_api.integrations.common.service.iService.IntegrationIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntegrationIssueServiceImpl implements IntegrationIssueService {

    // Most urgent kind first, then the oldest problem first.
    private static final Comparator<Issue> BY_URGENCY = Comparator.comparing(Issue::kind).thenComparing(Issue::since);

    // A failed Aguas delete rolls the cancellation back and the retry handlers can't redo it (they re-send the
    // movement instead), so it always needs someone to cancel the movement again.
    private static final String AGUAS_DELETE_OPERATION = "EQUIPMENT_DELETE";

    private final IntegrationIssueRepository issueRepository;

    @Value("${integrations.aguas.max-retries}")
    private int maxRetries;

    @Value("${app.integrations.stuck-after-minutes}")
    private int stuckAfterMinutes;

    @Value("${app.integrations.never-sent-lookback-days}")
    private int neverSentLookbackDays;

    @Override
    @Transactional(readOnly = true)
    public IntegrationIssuesResponse getIssues() {
        var now         = LocalDateTime.now();
        var stuckBefore = now.minusMinutes(stuckAfterMinutes);

        var logIssues = issueRepository.findLogIssues(stuckBefore);
        var neverSent = issueRepository.findNeverSent(stuckBefore, now.minusDays(neverSentLookbackDays));
        var truncated = logIssues.size() >= IntegrationIssueRepository.MAX_ISSUES
                || neverSent.size() >= IntegrationIssueRepository.MAX_ISSUES;

        var rows = new ArrayList<>(logIssues);
        rows.addAll(neverSent);
        var issues = rows.stream().map(this::toIssue).sorted(BY_URGENCY).toList();
        return new IntegrationIssuesResponse(summarize(issues), issues, truncated);
    }

    private Issue toIssue(IntegrationIssueRow row) {
        return new Issue(classify(row), row.integration(), row.operation(), row.entityName(), row.entityId(),
                row.routeCode(), row.entityKind(), row.entityDate(), row.logId(), row.retryCount(), maxRetries,
                row.lastError(), row.since());
    }

    private IntegrationIssueKind classify(IntegrationIssueRow row) {
        if (row.logId() == null) {
            return IntegrationIssueKind.NEVER_SENT;
        }
        if (!IntegrationStatus.ERROR.name().equals(row.logStatus())) {
            return IntegrationIssueKind.STUCK;
        }
        if (AGUAS_DELETE_OPERATION.equals(row.operation()) || row.retryCount() >= maxRetries) {
            return IntegrationIssueKind.RETRIES_EXHAUSTED;
        }
        return IntegrationIssueKind.RETRYING;
    }

    private static Summary summarize(List<Issue> issues) {
        Map<IntegrationIssueKind, Integer> counts = new EnumMap<>(IntegrationIssueKind.class);
        issues.forEach(issue -> counts.merge(issue.kind(), 1, Integer::sum));
        return new Summary(
                counts.getOrDefault(IntegrationIssueKind.RETRIES_EXHAUSTED, 0),
                counts.getOrDefault(IntegrationIssueKind.STUCK, 0),
                counts.getOrDefault(IntegrationIssueKind.NEVER_SENT, 0),
                counts.getOrDefault(IntegrationIssueKind.RETRYING, 0));
    }
}
