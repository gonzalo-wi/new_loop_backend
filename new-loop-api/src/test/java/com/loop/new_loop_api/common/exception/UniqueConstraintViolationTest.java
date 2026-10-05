package com.loop.new_loop_api.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueConstraintViolationTest {

    private static final String CONSTRAINT = "uq_stock_controls_type_route_date_active";

    @Test
    void shouldMatchWhenCausedByTheGivenConstraint() {
        var ex = violation(CONSTRAINT);

        assertThat(UniqueConstraintViolation.isCausedBy(ex, CONSTRAINT)).isTrue();
    }

    @Test
    void shouldMatchConstraintNameIgnoringCase() {
        var ex = violation(CONSTRAINT.toUpperCase());

        assertThat(UniqueConstraintViolation.isCausedBy(ex, CONSTRAINT)).isTrue();
    }

    @Test
    void shouldNotMatchAnotherConstraint() {
        var ex = violation("fk_stock_controls_route");

        assertThat(UniqueConstraintViolation.isCausedBy(ex, CONSTRAINT)).isFalse();
    }

    @Test
    void shouldNotMatchWhenNoConstraintViolationInCauseChain() {
        var ex = new DataIntegrityViolationException("boom", new SQLException("value too long"));

        assertThat(UniqueConstraintViolation.isCausedBy(ex, CONSTRAINT)).isFalse();
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        var cause = new ConstraintViolationException("duplicate key", new SQLException("23505"), constraintName);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
