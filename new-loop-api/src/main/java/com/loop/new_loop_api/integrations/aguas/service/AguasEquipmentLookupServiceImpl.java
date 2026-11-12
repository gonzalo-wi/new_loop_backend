package com.loop.new_loop_api.integrations.aguas.service;

import com.loop.new_loop_api.integrations.aguas.client.AguasEquipmentLookupClient;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentCheck;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentLookupService;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.metrics.IntegrationCallMetrics;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AguasEquipmentLookupServiceImpl implements AguasEquipmentLookupService {

    private static final Logger log = LoggerFactory.getLogger(AguasEquipmentLookupServiceImpl.class);

    private final AguasEquipmentLookupClient client;
    private final IntegrationCallMetrics     integrationCallMetrics;
    private final Executor                   lookupExecutor;
    private final Duration                   deadline;
    // Owned pool, kept off the context on purpose: an Executor bean would replace Boot's default @Async executor.
    private final ExecutorService            ownedPool;

    @Autowired
    public AguasEquipmentLookupServiceImpl(AguasEquipmentLookupClient client,
                                           IntegrationCallMetrics integrationCallMetrics,
                                           @Value("${integrations.aguas-equipment.lookup-concurrency:8}") int concurrency,
                                           @Value("${integrations.aguas-equipment.lookup-deadline-ms:5000}") long deadlineMs) {
        this(client, integrationCallMetrics, Executors.newFixedThreadPool(concurrency), Duration.ofMillis(deadlineMs));
    }

    AguasEquipmentLookupServiceImpl(AguasEquipmentLookupClient client, IntegrationCallMetrics integrationCallMetrics,
                                    Executor lookupExecutor, Duration deadline) {
        this.client                 = client;
        this.integrationCallMetrics = integrationCallMetrics;
        this.lookupExecutor         = lookupExecutor;
        this.deadline               = deadline;
        this.ownedPool              = lookupExecutor instanceof ExecutorService pool ? pool : null;
    }

    @PreDestroy
    void shutdown() {
        if (ownedPool != null) ownedPool.shutdownNow();
    }

    /**
     * Aguas answers one serial per call, so lookups run in parallel. The whole batch is bounded by
     * {@code deadline}: this also runs inside the create transaction, and a hung Aguas must not hold it.
     */
    @Override
    public List<AguasEquipmentCheck> check(List<String> serials) {
        var futures = serials.stream()
                .map(String::trim)
                .filter(serial -> !serial.isEmpty())
                .distinct()
                .map(serial -> CompletableFuture.supplyAsync(() -> lookup(serial), lookupExecutor)
                        .completeOnTimeout(timedOut(serial), deadline.toMillis(), TimeUnit.MILLISECONDS))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    @Override
    public Set<String> findMissingSerials(List<String> serials) {
        return check(serials).stream()
                .filter(check -> Boolean.FALSE.equals(check.existe()))
                .map(check -> check.serie().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    /** Never throws: any failure becomes an "unknown" check so Aguas being down never blocks a load. */
    private AguasEquipmentCheck lookup(String serial) {
        try {
            var response = client.findBySerial(serial);
            integrationCallMetrics.recordSuccess(IntegrationName.AGUAS);
            var data = response == null ? null : response.data();
            if (data == null || !response.success() || data.existe() == null) {
                log.warn("Aguas equipment lookup for serial {} gave no usable answer: {}", serial, response);
                return unknown(serial);
            }
            var equipo = data.equipo();
            return new AguasEquipmentCheck(serial, data.existe(), data.nroSerie(),
                    equipo == null ? null : equipo.ubicacion(),
                    equipo == null ? null : equipo.estado());
        } catch (Exception e) {
            log.error("Aguas equipment lookup failed for serial {}: {}: {}",
                    serial, e.getClass().getSimpleName(), e.getMessage());
            integrationCallMetrics.recordError(IntegrationName.AGUAS);
            return unknown(serial);
        }
    }

    private AguasEquipmentCheck timedOut(String serial) {
        log.warn("Aguas equipment lookup for serial {} did not finish within {} ms", serial, deadline.toMillis());
        return unknown(serial);
    }

    private static AguasEquipmentCheck unknown(String serial) {
        return new AguasEquipmentCheck(serial, null, null, null, null);
    }
}
