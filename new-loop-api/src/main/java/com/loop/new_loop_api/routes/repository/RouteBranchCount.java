package com.loop.new_loop_api.routes.repository;

import java.util.UUID;

/** One row of {@link RouteRepository#countByBranchAndActive()}: routes of a branch with a given active flag. */
public record RouteBranchCount(UUID branchId, String branchName, Boolean active, Long count) {
}
