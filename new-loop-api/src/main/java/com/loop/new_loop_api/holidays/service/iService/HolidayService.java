package com.loop.new_loop_api.holidays.service.iService;

import com.loop.new_loop_api.holidays.dto.CreateHolidayRequest;
import com.loop.new_loop_api.holidays.dto.HolidayResponse;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface HolidayService {

    /** Holidays from {@code from} onwards; without it, from the first day of the current month. */
    List<HolidayResponse> list(LocalDate from);

    HolidayResponse create(CreateHolidayRequest request);

    void delete(UUID id);

    /** Which of the given dates are holidays, in one query. */
    Set<LocalDate> holidaysAmong(Collection<LocalDate> dates);
}
