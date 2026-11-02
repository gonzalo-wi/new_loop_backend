package com.loop.new_loop_api.integrations.pac.service;

import com.loop.new_loop_api.integrations.pac.dto.PacArrivalResponse;
import com.loop.new_loop_api.integrations.pac.mapper.PacArrivalMapper;
import com.loop.new_loop_api.integrations.pac.service.iService.PacArrivalService;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Polled every minute by PAC, so it deliberately writes nothing to the audit log. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacArrivalServiceImpl implements PacArrivalService {

    private static final Comparator<PacArrivalResponse> BY_ARRIVAL = Comparator
            .comparing(PacArrivalResponse::arrivedAt)
            .thenComparing(PacArrivalResponse::routeCode)
            .thenComparing(PacArrivalResponse::branchName);

    private final Clock                  clock;
    private final StockControlRepository stockControlRepository;
    private final PacArrivalMapper       mapper;

    @Override
    @Transactional(readOnly = true)
    public List<PacArrivalResponse> getArrivals(LocalDate date) {
        var target = date != null ? date : clock.instant().atZone(PacArrivalMapper.ARGENTINA).toLocalDate();
        try {
            return stockControlRepository
                    .findControlsForDate(ControlType.ENTRY, target, ControlStatus.CANCELLED, null)
                    .stream()
                    .map(mapper::toResponse)
                    .filter(arrival -> Objects.nonNull(arrival.routeNumber()))
                    .sorted(BY_ARRIVAL)
                    .toList();
        } catch (DataAccessException e) {
            log.error("PAC arrivals lookup failed for {}", target, e);
            throw e;
        }
    }
}
