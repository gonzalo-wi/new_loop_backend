package com.loop.new_loop_api.dispensers.service.iService;

import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse;

import java.time.LocalDate;

public interface DispenserStatsService {

    DispenserStatsResponse getStats(LocalDate from, LocalDate to);
}
