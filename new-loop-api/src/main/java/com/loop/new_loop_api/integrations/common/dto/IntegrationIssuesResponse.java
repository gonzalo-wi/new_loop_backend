package com.loop.new_loop_api.integrations.common.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** truncated: a query hit IntegrationIssueRepository.MAX_ISSUES, so the list and the counts are partial. */
public record IntegrationIssuesResponse(Summary summary, List<Issue> issues, boolean truncated) {

    public record Summary(int retriesExhausted, int stuck, int neverSent, int retrying) {}

    /**
     * One record that did not reach Aguas/Odoo. logId is null for NEVER_SENT (there is no attempt to retry);
     * entityKind is the control type (EXIT/ENTRY) or movement type (LOAD/UNLOAD).
     */
    public record Issue(
            IntegrationIssueKind kind,
            String integration,
            String operation,
            String entityName,
            UUID entityId,
            String routeCode,
            String entityKind,
            LocalDate entityDate,
            UUID logId,
            int retryCount,
            int maxRetries,
            String lastError,
            LocalDateTime since) {}
}
