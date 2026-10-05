package com.loop.new_loop_api.common.config;

import com.loop.new_loop_api.support.PostgresIntegrationTest;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Scheduling on to get the real LockProvider; the retry job is pushed out so it never fires during the test.
@TestPropertySource(properties = {"app.scheduling.enabled=true", "integrations.aguas.retry-initial-delay-ms=86400000"})
class SchedulerLockConcurrencyTest extends PostgresIntegrationTest {

    @Autowired private LockProvider lockProvider;

    @Test
    void shouldRunEachExecutionOnASingleInstance() throws Exception {
        // Two "instances" racing for the same job: each has its own executor over the shared shedlock table.
        var lockName   = "test-job-" + UUID.randomUUID();
        var executions = new AtomicInteger();
        Callable<Boolean> instance = () -> {
            try {
                var result = new DefaultLockingTaskExecutor(lockProvider).executeWithLock(
                        () -> {
                            executions.incrementAndGet();
                            Thread.sleep(500);
                            return true;
                        },
                        new LockConfiguration(Instant.now(), lockName, Duration.ofMinutes(1), Duration.ofSeconds(5)));
                return result.wasExecuted();
            } catch (Throwable t) {
                throw new IllegalStateException("Locked task failed", t);
            }
        };

        var outcomes = runConcurrently(List.of(instance, instance));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(executions).hasValue(1);
    }

    @Test
    void shouldBlockSecondLockUntilFirstIsReleased() {
        var config = new LockConfiguration(Instant.now(), "test-lock-" + UUID.randomUUID(), Duration.ofMinutes(1), Duration.ZERO);

        var first = lockProvider.lock(config);
        assertThat(first).isPresent();
        assertThat(lockProvider.lock(config)).isEmpty();

        first.get().unlock();
        assertThat(lockProvider.lock(config)).isPresent();
    }
}
