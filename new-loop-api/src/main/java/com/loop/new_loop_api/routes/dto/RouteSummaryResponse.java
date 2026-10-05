package com.loop.new_loop_api.routes.dto;

import java.util.List;
import java.util.UUID;

public record RouteSummaryResponse(
        long total,
        long active,
        long inactive,
        List<BranchSummary> byBranch) {

    public record BranchSummary(UUID branchId, String branchName, long active, long inactive) {}
}
