package com.loop.new_loop_api.settlements.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class SettlementConfig {

    public static final String PAC_EXECUTOR = "pacSettlementExecutor";

    /** PAC lookups only wait on the network: virtual threads, so a hung PAC never ties up a pool. */
    @Bean(name = PAC_EXECUTOR, destroyMethod = "close")
    public ExecutorService pacSettlementExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
