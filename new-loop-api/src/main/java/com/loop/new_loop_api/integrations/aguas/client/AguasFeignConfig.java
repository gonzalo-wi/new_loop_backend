package com.loop.new_loop_api.integrations.aguas.client;

import feign.Logger;
import org.springframework.context.annotation.Bean;

/** Enables full HTTP request/response logging (headers included) for the Aguas client. */
public class AguasFeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
