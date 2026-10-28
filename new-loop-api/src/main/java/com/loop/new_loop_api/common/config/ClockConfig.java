package com.loop.new_loop_api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** "Now" is injected so business rules that depend on time can be tested with a fixed clock. */
@Configuration
public class ClockConfig {

    // The JVM default zone is set to Argentina at startup (NewLoopApiApplication), so calendar dates follow it.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
