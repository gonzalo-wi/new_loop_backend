package com.loop.new_loop_api.support;

import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasIntegrationService;
import com.loop.new_loop_api.integrations.jmobile.service.iService.UnregisteredDispenserService;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooDispatchService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Base for tests that need a real PostgreSQL (partial unique indexes, row versioning, ShedLock).
 * Runs only when LOOP_TEST_DB_URL points to a local database, so it can never hit a shared one.
 * Every outbound integration is mocked and its URL points to a closed local port as a second guard.
 */
@SpringBootTest
@RequiresLocalPostgres
@TestPropertySource(properties = {
        "spring.datasource.url=${LOOP_TEST_DB_URL}",
        "spring.datasource.username=${LOOP_TEST_DB_USERNAME:loop_test}",
        "spring.datasource.password=${LOOP_TEST_DB_PASSWORD:}",
        "spring.jpa.show-sql=false",
        "app.scheduling.enabled=false",
        "integrations.aguas.base-url=http://127.0.0.1:9",
        "integrations.aguas-equipment.base-url=http://127.0.0.1:9",
        "integrations.jmobile.base-url=http://127.0.0.1:9",
        "integrations.odoo.base-url=http://127.0.0.1:9",
        "integrations.odoo.dispatch.base-url=http://127.0.0.1:9",
        "integrations.powerfleet.base-url=http://127.0.0.1:9"
})
public abstract class PostgresIntegrationTest {

    @MockitoBean protected AguasIntegrationService      aguasIntegrationService;
    @MockitoBean protected AguasEquipmentService        aguasEquipmentService;
    @MockitoBean protected OdooDispatchService          odooDispatchService;
    @MockitoBean protected UnregisteredDispenserService unregisteredDispenserService;

    /** Starts every task at the same instant and returns their outcomes (value or thrown exception). */
    protected static <T> List<Outcome<T>> runConcurrently(List<Callable<T>> tasks) throws InterruptedException {
        var executor = Executors.newFixedThreadPool(tasks.size());
        var start    = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<T>>();
            for (var task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            var outcomes = new ArrayList<Outcome<T>>();
            for (var future : futures) {
                outcomes.add(Outcome.of(future));
            }
            return outcomes;
        } finally {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    public record Outcome<T>(T value, Throwable error) {

        static <T> Outcome<T> of(Future<T> future) throws InterruptedException {
            try {
                return new Outcome<>(future.get(30, TimeUnit.SECONDS), null);
            } catch (java.util.concurrent.ExecutionException ex) {
                return new Outcome<>(null, ex.getCause());
            } catch (java.util.concurrent.TimeoutException ex) {
                return new Outcome<>(null, ex);
            }
        }

        public boolean succeeded() {
            return error == null;
        }
    }
}
