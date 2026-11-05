package com.loop.new_loop_api.holidays.dto;

import com.loop.new_loop_api.holidays.entity.Holiday;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(UUID id, LocalDate date, String description) {

    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getDescription());
    }
}
