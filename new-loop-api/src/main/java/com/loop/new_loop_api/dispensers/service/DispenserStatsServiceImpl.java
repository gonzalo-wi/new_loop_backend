package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.Quality;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.Range;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.Volume;
import com.loop.new_loop_api.dispensers.exception.InvalidStatsRangeException;
import com.loop.new_loop_api.dispensers.repository.DispenserStatsRepository;
import com.loop.new_loop_api.dispensers.repository.DispenserStatsRepository.QualityTotals;
import com.loop.new_loop_api.dispensers.repository.DispenserStatsRepository.VolumeTotals;
import com.loop.new_loop_api.dispensers.service.iService.DispenserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class DispenserStatsServiceImpl implements DispenserStatsService {

    static final int MAX_RANGE_DAYS = 92;

    private static final double PERCENT       = 100.0;
    private static final double ROUND_FACTOR  = 100.0;

    private final DispenserStatsRepository statsRepository;

    @Override
    @Transactional(readOnly = true)
    public DispenserStatsResponse getStats(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return new DispenserStatsResponse(
                new Range(from, to),
                buildVolume(statsRepository.findVolume(from, to)),
                statsRepository.findDaily(from, to),
                statsRepository.findHeatmap(from, to),
                statsRepository.findUsers(from, to),
                statsRepository.findTechnicians(from, to),
                buildQuality(from, to));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw InvalidStatsRangeException.endBeforeStart(from, to);
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw InvalidStatsRangeException.tooWide(MAX_RANGE_DAYS);
        }
    }

    private Volume buildVolume(VolumeTotals totals) {
        int movements = totals.installMovements() + totals.withdrawalMovements();
        int serials   = totals.installedDispensers() + totals.withdrawnDispensers();
        Double withdrawalsPer100 = totals.installedDispensers() == 0 ? null
                : round((double) totals.withdrawnDispensers() / totals.installedDispensers() * PERCENT);
        Double avgSerials = movements == 0 ? null : round((double) serials / movements);
        return new Volume(totals.installMovements(), totals.withdrawalMovements(),
                totals.installedDispensers(), totals.withdrawnDispensers(),
                totals.installedDispensers() - totals.withdrawnDispensers(),
                withdrawalsPer100, avgSerials, totals.maxSerialsPerMovement());
    }

    private Quality buildQuality(LocalDate from, LocalDate to) {
        QualityTotals totals = statsRepository.findQualityTotals(from, to);
        return new Quality(totals.activeMovements(), statsRepository.countCancelled(from, to),
                totals.sentToAguas(), totals.skippedUnregistered(), totals.aguasErrors(), totals.odooErrors(),
                totals.withExcludedSerials(), totals.withoutUser(), statsRepository.findQualityIssues(from, to));
    }

    private double round(double value) {
        return Math.round(value * ROUND_FACTOR) / ROUND_FACTOR;
    }
}
