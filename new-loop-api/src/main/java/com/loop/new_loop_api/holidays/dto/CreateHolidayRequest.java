package com.loop.new_loop_api.holidays.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateHolidayRequest(
        @NotNull(message = "date is required") LocalDate date,
        @Size(max = 120, message = "description must be at most 120 characters") String description) {
}
