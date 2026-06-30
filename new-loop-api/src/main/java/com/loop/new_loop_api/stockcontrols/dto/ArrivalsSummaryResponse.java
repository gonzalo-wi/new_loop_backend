package com.loop.new_loop_api.stockcontrols.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ArrivalsSummaryResponse {

    private LocalDate                    date;
    private int                          totalExpected;
    private int                          arrived;
    private int                          pending;
    private List<PendingArrivalResponse> pendingRoutes;
}
