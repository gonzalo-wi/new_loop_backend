package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.DailyPoint;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.HeatmapCell;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.QualityIssue;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.TechnicianStats;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.UserStats;
import com.loop.new_loop_api.dispensers.dto.QualityIssueKind;
import com.loop.new_loop_api.dispensers.exception.InvalidStatsRangeException;
import com.loop.new_loop_api.dispensers.repository.DispenserStatsRepository;
import com.loop.new_loop_api.dispensers.repository.DispenserStatsRepository.QualityTotals;
import com.loop.new_loop_api.dispensers.repository.DispenserStatsRepository.VolumeTotals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispenserStatsServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

    @Mock private DispenserStatsRepository statsRepository;

    @InjectMocks private DispenserStatsServiceImpl service;

    private void stubRepository(LocalDate from, LocalDate to, VolumeTotals volume) {
        when(statsRepository.findVolume(from, to)).thenReturn(volume);
        when(statsRepository.findDaily(from, to)).thenReturn(List.of());
        when(statsRepository.findHeatmap(from, to)).thenReturn(List.of());
        when(statsRepository.findUsers(from, to)).thenReturn(List.of());
        when(statsRepository.findTechnicians(from, to)).thenReturn(List.of());
        when(statsRepository.findQualityTotals(from, to)).thenReturn(new QualityTotals(0, 0, 0, 0, 0, 0, 0));
        when(statsRepository.countCancelled(from, to)).thenReturn(0);
        when(statsRepository.findQualityIssues(from, to)).thenReturn(List.of());
    }

    @Test
    void getStats_throws_whenToIsBeforeFrom() {
        LocalDate to = FROM.minusDays(1);

        assertThatThrownBy(() -> service.getStats(FROM, to))
                .isInstanceOf(InvalidStatsRangeException.class)
                .hasMessageContaining("must not be before");
        verifyNoInteractions(statsRepository);
    }

    @Test
    void getStats_throws_whenRangeExceeds92Days() {
        LocalDate to = FROM.plusDays(92);

        assertThatThrownBy(() -> service.getStats(FROM, to))
                .isInstanceOf(InvalidStatsRangeException.class)
                .hasMessageContaining("92");
        verifyNoInteractions(statsRepository);
    }

    @Test
    void getStats_succeeds_whenRangeIsExactly92DaysInclusive() {
        LocalDate to = FROM.plusDays(91);
        stubRepository(FROM, to, new VolumeTotals(0, 0, 0, 0, 0));

        DispenserStatsResponse response = service.getStats(FROM, to);

        assertThat(response.range().from()).isEqualTo(FROM);
        assertThat(response.range().to()).isEqualTo(to);
    }

    @Test
    void getStats_succeeds_whenFromEqualsTo() {
        stubRepository(FROM, FROM, new VolumeTotals(0, 0, 0, 0, 0));

        DispenserStatsResponse response = service.getStats(FROM, FROM);

        assertThat(response.range().from()).isEqualTo(FROM);
        assertThat(response.range().to()).isEqualTo(FROM);
    }

    @Test
    void getStats_computesNetPer100AndAverage_fromVolumeTotals() {
        LocalDate to = FROM.plusDays(10);
        stubRepository(FROM, to, new VolumeTotals(4, 6, 30, 10, 7));

        DispenserStatsResponse.Volume volume = service.getStats(FROM, to).volume();

        assertThat(volume.installMovements()).isEqualTo(4);
        assertThat(volume.withdrawalMovements()).isEqualTo(6);
        assertThat(volume.installedDispensers()).isEqualTo(30);
        assertThat(volume.withdrawnDispensers()).isEqualTo(10);
        assertThat(volume.netDispensers()).isEqualTo(20);
        assertThat(volume.withdrawalsPer100()).isEqualTo(33.33);
        assertThat(volume.avgSerialsPerMovement()).isEqualTo(4.0);
        assertThat(volume.maxSerialsPerMovement()).isEqualTo(7);
    }

    @Test
    void getStats_netIsNegative_whenMoreWithdrawnThanInstalled() {
        stubRepository(FROM, FROM, new VolumeTotals(1, 1, 2, 5, 5));

        DispenserStatsResponse.Volume volume = service.getStats(FROM, FROM).volume();

        assertThat(volume.netDispensers()).isEqualTo(-3);
        assertThat(volume.withdrawalsPer100()).isEqualTo(250.0);
    }

    @Test
    void getStats_withdrawalsPer100IsNull_whenNothingInstalled() {
        stubRepository(FROM, FROM, new VolumeTotals(0, 2, 0, 4, 2));

        DispenserStatsResponse.Volume volume = service.getStats(FROM, FROM).volume();

        assertThat(volume.withdrawalsPer100()).isNull();
        assertThat(volume.avgSerialsPerMovement()).isEqualTo(2.0);
    }

    @Test
    void getStats_averagesAreNull_whenNoMovements() {
        stubRepository(FROM, FROM, new VolumeTotals(0, 0, 0, 0, 0));

        DispenserStatsResponse.Volume volume = service.getStats(FROM, FROM).volume();

        assertThat(volume.withdrawalsPer100()).isNull();
        assertThat(volume.avgSerialsPerMovement()).isNull();
        assertThat(volume.netDispensers()).isZero();
    }

    @Test
    void getStats_assemblesAllSectionsFromRepository() {
        LocalDate to = FROM.plusDays(1);
        var daily = List.of(new DailyPoint(FROM, 3, 1, 1, 1), new DailyPoint(to, 0, 0, 0, 0));
        var heatmap = List.of(new HeatmapCell(0, 9, 2));
        var users = List.of(new UserStats("ana", 2, 1, 1, 4, 0.0, 0.5, 0.0, 12.5));
        var technicians = List.of(new TechnicianStats("Sin técnico", 1, 3, 1, 2));
        var issues = List.of(new QualityIssue(UUID.randomUUID(), LocalDateTime.of(2026, 8, 1, 10, 0), "LOAD",
                "R1", "Juan", "ana", List.of(QualityIssueKind.AGUAS_ERROR, QualityIssueKind.EXCLUDED), 1));

        when(statsRepository.findVolume(FROM, to)).thenReturn(new VolumeTotals(1, 1, 3, 1, 3));
        when(statsRepository.findDaily(FROM, to)).thenReturn(daily);
        when(statsRepository.findHeatmap(FROM, to)).thenReturn(heatmap);
        when(statsRepository.findUsers(FROM, to)).thenReturn(users);
        when(statsRepository.findTechnicians(FROM, to)).thenReturn(technicians);
        when(statsRepository.findQualityTotals(FROM, to)).thenReturn(new QualityTotals(2, 1, 0, 1, 0, 1, 0));
        when(statsRepository.countCancelled(FROM, to)).thenReturn(5);
        when(statsRepository.findQualityIssues(FROM, to)).thenReturn(issues);

        DispenserStatsResponse response = service.getStats(FROM, to);

        assertThat(response.daily()).isSameAs(daily);
        assertThat(response.heatmap()).isSameAs(heatmap);
        assertThat(response.users()).isSameAs(users);
        assertThat(response.technicians()).isSameAs(technicians);
        var quality = response.quality();
        assertThat(quality.activeMovements()).isEqualTo(2);
        assertThat(quality.cancelledMovements()).isEqualTo(5);
        assertThat(quality.sentToAguas()).isEqualTo(1);
        assertThat(quality.skippedUnregistered()).isZero();
        assertThat(quality.aguasErrors()).isEqualTo(1);
        assertThat(quality.odooErrors()).isZero();
        assertThat(quality.withExcludedSerials()).isEqualTo(1);
        assertThat(quality.withoutUser()).isZero();
        assertThat(quality.issues()).isSameAs(issues);
    }
}
