package com.loop.new_loop_api.holidays.repository;

import com.loop.new_loop_api.holidays.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    boolean existsByDate(LocalDate date);

    List<Holiday> findByDateGreaterThanEqualOrderByDateAsc(LocalDate from);

    List<Holiday> findByDateIn(Collection<LocalDate> dates);
}
