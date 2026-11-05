package com.loop.new_loop_api.holidays.service;

import com.loop.new_loop_api.common.exception.UniqueConstraintViolation;
import com.loop.new_loop_api.holidays.dto.CreateHolidayRequest;
import com.loop.new_loop_api.holidays.dto.HolidayResponse;
import com.loop.new_loop_api.holidays.entity.Holiday;
import com.loop.new_loop_api.holidays.exception.HolidayAlreadyExistsException;
import com.loop.new_loop_api.holidays.exception.HolidayNotFoundException;
import com.loop.new_loop_api.holidays.repository.HolidayRepository;
import com.loop.new_loop_api.holidays.service.iService.HolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayService {

    // From V42; must match the migration's name.
    private static final String DATE_UNIQUE_CONSTRAINT = "uq_holidays_date";

    private final HolidayRepository holidayRepository;
    private final Clock             clock;

    @Override
    @Transactional(readOnly = true)
    public List<HolidayResponse> list(LocalDate from) {
        var start = from != null ? from : LocalDate.now(clock).withDayOfMonth(1);
        return holidayRepository.findByDateGreaterThanEqualOrderByDateAsc(start).stream()
                .map(HolidayResponse::from)
                .toList();
    }

    // No restriction on past dates: an unplanned holiday is loaded the same day (spec 005, FR-2).
    @Override
    @Transactional
    public HolidayResponse create(CreateHolidayRequest request) {
        if (holidayRepository.existsByDate(request.date())) {
            throw new HolidayAlreadyExistsException(request.date());
        }
        var holiday = Holiday.builder()
                .date(request.date())
                .description(blankToNull(request.description()))
                .build();
        try {
            return HolidayResponse.from(holidayRepository.saveAndFlush(holiday));
        } catch (DataIntegrityViolationException ex) {
            // Two admins adding the same date at once both pass the check above; the constraint decides.
            if (UniqueConstraintViolation.isCausedBy(ex, DATE_UNIQUE_CONSTRAINT)) {
                throw new HolidayAlreadyExistsException(request.date());
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var holiday = holidayRepository.findById(id).orElseThrow(() -> new HolidayNotFoundException(id));
        holidayRepository.delete(holiday);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocalDate> holidaysAmong(Collection<LocalDate> dates) {
        if (dates.isEmpty()) return Set.of();
        return holidayRepository.findByDateIn(dates).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
