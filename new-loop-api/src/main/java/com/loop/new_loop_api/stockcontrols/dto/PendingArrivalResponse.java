package com.loop.new_loop_api.stockcontrols.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class PendingArrivalResponse {

    private UUID      routeId;
    private String    routeCode;
    private UUID      branchId;
    private String    branchName;
    private UUID      exitControlId;
    private LocalDate controlDate;
}
