package com.loop.new_loop_api.integrations.common.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Raw row behind an integration issue; logId/logStatus are null when no send was ever attempted. */
public record IntegrationIssueRow(
        UUID logId,
        String integration,
        String operation,
        String entityName,
        UUID entityId,
        String logStatus,
        int retryCount,
        String lastError,
        LocalDateTime since,
        String routeCode,
        String entityKind,
        LocalDate entityDate) {
}
