package com.loop.new_loop_api.common.config;

import com.loop.new_loop_api.audit.service.AuditServiceImpl;
import com.loop.new_loop_api.integrations.aguas.scheduler.AguasRetryScheduler;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class)
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void shouldEnableSchedulingWithLockProviderByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(LockProvider.class));
    }

    @Test
    void shouldNotScheduleAnythingWhenDisabled() {
        contextRunner.withPropertyValues("app.scheduling.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(LockProvider.class)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void everyScheduledJobShouldHoldADistinctClusterLock() {
        var retry = scheduledMethod(AguasRetryScheduler.class);
        var purge = scheduledMethod(AuditServiceImpl.class);

        assertThat(retry.getAnnotation(SchedulerLock.class)).isNotNull();
        assertThat(purge.getAnnotation(SchedulerLock.class)).isNotNull();
        assertThat(retry.getAnnotation(SchedulerLock.class).name())
                .isNotEqualTo(purge.getAnnotation(SchedulerLock.class).name());
    }

    @Test
    void retryJobShouldNotFireRightAtStartup() {
        var scheduled = scheduledMethod(AguasRetryScheduler.class).getAnnotation(Scheduled.class);

        assertThat(scheduled.initialDelayString()).isNotBlank();
    }

    private static Method scheduledMethod(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .findFirst()
                .orElseThrow();
    }
}
