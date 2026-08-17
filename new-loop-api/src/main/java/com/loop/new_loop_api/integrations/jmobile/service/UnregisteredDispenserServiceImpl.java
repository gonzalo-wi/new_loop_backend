package com.loop.new_loop_api.integrations.jmobile.service;

import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.metrics.IntegrationCallMetrics;
import com.loop.new_loop_api.integrations.jmobile.client.UnregisteredDispenserClient;
import com.loop.new_loop_api.integrations.jmobile.dto.UnregisteredDispenser;
import com.loop.new_loop_api.integrations.jmobile.service.iService.UnregisteredDispenserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UnregisteredDispenserServiceImpl implements UnregisteredDispenserService {

    private static final Logger log = LoggerFactory.getLogger(UnregisteredDispenserServiceImpl.class);

    private final UnregisteredDispenserClient unregisteredDispenserClient;
    private final IntegrationCallMetrics      integrationCallMetrics;

    @Override
    public Set<String> findUnregisteredSerials(LocalDate date) {
        try {
            var response = unregisteredDispenserClient.getUnregisteredDispensers(date.toString());
            if (response == null || response.getListado() == null) {
                log.warn("jMobile unregistered dispensers returned no list for {}", date);
                integrationCallMetrics.recordSuccess(IntegrationName.JMOBILE);
                return Set.of();
            }

            var serials = new HashSet<String>();
            for (UnregisteredDispenser dispenser : response.getListado()) {
                var serial = normalize(dispenser.getNroSerie());
                if (serial != null) serials.add(serial);
            }
            log.info("jMobile reported {} unregistered dispenser(s) for {}", serials.size(), date);
            integrationCallMetrics.recordSuccess(IntegrationName.JMOBILE);
            return serials;
        } catch (Exception e) {
            // Fail-open: if the lookup is unavailable the movement is sent as scanned.
            log.error("Failed reading jMobile unregistered dispensers for {}: {}: {}",
                    date, e.getClass().getSimpleName(), e.getMessage());
            integrationCallMetrics.recordError(IntegrationName.JMOBILE);
            return Set.of();
        }
    }

    @Override
    public String normalize(String serial) {
        if (serial == null) return null;
        var trimmed = serial.trim().toUpperCase();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
