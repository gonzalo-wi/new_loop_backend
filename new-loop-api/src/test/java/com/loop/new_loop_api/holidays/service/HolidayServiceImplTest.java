package com.loop.new_loop_api.holidays.service;

import com.loop.new_loop_api.holidays.dto.CreateHolidayRequest;
import com.loop.new_loop_api.holidays.entity.Holiday;
import com.loop.new_loop_api.holidays.exception.HolidayAlreadyExistsException;
import com.loop.new_loop_api.holidays.exception.HolidayNotFoundException;
import com.loop.new_loop_api.holidays.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayServiceImplTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    // 2026-11-04 10:00 in Argentina.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T13:00:00Z"), ARGENTINA);

    @Mock private HolidayRepository holidayRepository;

    private HolidayServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HolidayServiceImpl(holidayRepository, CLOCK);
    }

    @Test
    void shouldListFromTheFirstDayOfTheCurrentMonth_when_noDateIsGiven() {
        when(holidayRepository.findByDateGreaterThanEqualOrderByDateAsc(LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of(holiday(LocalDate.of(2026, 11, 23), "Soberanía Nacional")));

        var result = service.list(null);

        assertThat(result).singleElement()
                .satisfies(h -> assertThat(h.date()).isEqualTo(LocalDate.of(2026, 11, 23)));
    }

    @Test
    void shouldListFromTheGivenDate() {
        service.list(LocalDate.of(2026, 1, 1));

        verify(holidayRepository).findByDateGreaterThanEqualOrderByDateAsc(LocalDate.of(2026, 1, 1));
    }

    @Test
    void shouldCreateAHolidayForToday() {
        var today = LocalDate.now(CLOCK);
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create(new CreateHolidayRequest(today, "  Feriado puente  "));

        assertThat(result.date()).isEqualTo(today);
        assertThat(result.description()).isEqualTo("Feriado puente");
    }

    @Test
    void shouldCreateAHolidayInThePast() {
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create(new CreateHolidayRequest(LocalDate.of(2026, 10, 12), null));

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void shouldStoreABlankDescriptionAsNull() {
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateHolidayRequest(LocalDate.of(2026, 12, 8), "   "));

        var captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    void shouldRefuseADateThatIsAlreadyAHoliday() {
        var date = LocalDate.of(2026, 12, 25);
        when(holidayRepository.existsByDate(date)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateHolidayRequest(date, "Navidad")))
                .isInstanceOf(HolidayAlreadyExistsException.class)
                .hasMessageContaining("2026-12-25");
        verify(holidayRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRefuseADuplicateThatSlippedPastTheCheck_whenTheConstraintRejectsIt() {
        var date = LocalDate.of(2026, 12, 25);
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenThrow(new DataIntegrityViolationException(
                "insert failed", new ConstraintViolationException("duplicate key", null, "uq_holidays_date")));

        assertThatThrownBy(() -> service.create(new CreateHolidayRequest(date, "Navidad")))
                .isInstanceOf(HolidayAlreadyExistsException.class);
    }

    @Test
    void shouldDeleteAHoliday() {
        var holiday = holiday(LocalDate.of(2026, 12, 25), "Navidad");
        when(holidayRepository.findById(holiday.getId())).thenReturn(Optional.of(holiday));

        service.delete(holiday.getId());

        verify(holidayRepository).delete(holiday);
    }

    @Test
    void shouldAnswerNotFound_when_deletingAnUnknownHoliday() {
        var id = UUID.randomUUID();
        when(holidayRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(HolidayNotFoundException.class);
    }

    @Test
    void shouldTellWhichDatesAreHolidays() {
        var holiday = LocalDate.of(2026, 11, 23);
        var workday = LocalDate.of(2026, 11, 24);
        when(holidayRepository.findByDateIn(List.of(holiday, workday)))
                .thenReturn(List.of(holiday(holiday, null)));

        assertThat(service.holidaysAmong(List.of(holiday, workday))).containsExactly(holiday);
    }

    @Test
    void shouldNotQuery_when_askedAboutNoDates() {
        assertThat(service.holidaysAmong(List.of())).isEmpty();
        verifyNoInteractions(holidayRepository);
    }

    private Holiday holiday(LocalDate date, String description) {
        return Holiday.builder().id(UUID.randomUUID()).date(date).description(description).build();
    }
}
