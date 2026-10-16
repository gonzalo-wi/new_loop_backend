package com.loop.new_loop_api.stockcontrols.repository;

import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockControlSpecificationTest {

    @Mock private Root<StockControl> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;
    @Mock private Path<LocalDateTime> createdAt;
    @Mock private Path<LocalDate> controlDate;

    @BeforeEach
    void setUp() {
        when(root.<LocalDateTime>get("createdAt")).thenReturn(createdAt);
        when(root.<LocalDate>get("controlDate")).thenReturn(controlDate);
        var predicate = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
    }

    @Test
    void shouldFilterCreatedRangeCoveringWholeDays() {
        var day = LocalDate.of(2026, 10, 15);

        StockControlSpecification.withFilters(null, null, null, null, null, null, null, day, day)
                .toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(createdAt, day.atStartOfDay());
        verify(cb).lessThan(createdAt, day.plusDays(1).atStartOfDay());
        verify(cb, never()).greaterThanOrEqualTo(eq(controlDate), any(LocalDate.class));
    }

    @Test
    void shouldKeepFilteringControlDateWithFromTo() {
        var day = LocalDate.of(2026, 10, 16);

        StockControlSpecification.withFilters(null, null, null, null, null, day, day, null, null)
                .toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(controlDate, day);
        verify(cb).lessThanOrEqualTo(controlDate, day);
        verify(cb, never()).greaterThanOrEqualTo(eq(createdAt), any(LocalDateTime.class));
    }
}
