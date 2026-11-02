package com.loop.new_loop_api.integrations.pac.service.iService;

import com.loop.new_loop_api.integrations.pac.dto.PacArrivalResponse;

import java.time.LocalDate;
import java.util.List;

public interface PacArrivalService {

    /** Routes that came back on {@code date}, or today in Argentina when it is null, ordered by arrival time. */
    List<PacArrivalResponse> getArrivals(LocalDate date);
}
