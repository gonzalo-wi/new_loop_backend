package com.loop.new_loop_api.loadforecast.dto;

import java.time.LocalDate;
import java.util.List;

/** Empty basedOnDates and items mean there is no past load to base a forecast on. */
public record LoadForecastResponse(String routeCode, LocalDate loadDate, List<LocalDate> basedOnDates,
                                   List<LoadForecastItemResponse> items) {
}
