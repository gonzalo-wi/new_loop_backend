package com.loop.new_loop_api.dispensers.metrics;

import com.loop.new_loop_api.common.metrics.MetricsTags;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Business metrics for dispenser movements: submissions to Aguas (LOAD/UNLOAD) and
 * serials skipped because jMobile reports the dispenser as not registered.
 */
@Component
@RequiredArgsConstructor
public class DispenserMovementMetrics {

    private static final String SENT_COUNTER          = "loop_dispenser_movement_aguas_enviado_total";
    private static final String REJECTED_COUNTER       = "loop_dispenser_movement_aguas_rechazado_total";
    private static final String UNREGISTERED_COUNTER   = "loop_dispenser_no_registrado_total";
    private static final String UNAVAILABLE_ODOO_COUNTER = "loop_dispenser_no_disponible_odoo_total";

    private final MeterRegistry meterRegistry;

    public void recordSent(DispenserMovementType type) {
        Counter.builder(SENT_COUNTER)
                .description("Total dispenser movements successfully sent to Aguas")
                .tag(MetricsTags.MOVEMENT_TYPE, type.name())
                .register(meterRegistry)
                .increment();
    }

    public void recordRejected(DispenserMovementType type) {
        Counter.builder(REJECTED_COUNTER)
                .description("Total dispenser movements rejected/errored by Aguas")
                .tag(MetricsTags.MOVEMENT_TYPE, type.name())
                .register(meterRegistry)
                .increment();
    }

    public void recordUnregisteredSerialsExcluded(int count) {
        if (count <= 0) return;
        Counter.builder(UNREGISTERED_COUNTER)
                .description("Total dispenser serials excluded from Aguas/Odoo as unregistered in jMobile")
                .register(meterRegistry)
                .increment(count);
    }

    public void recordUnavailableInOdooExcluded(int count) {
        if (count <= 0) return;
        Counter.builder(UNAVAILABLE_ODOO_COUNTER)
                .description("Total dispenser serials excluded from Aguas/Odoo as unavailable in Odoo at load time")
                .register(meterRegistry)
                .increment(count);
    }
}
