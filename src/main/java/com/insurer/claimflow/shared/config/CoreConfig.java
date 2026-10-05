package com.insurer.claimflow.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class CoreConfig {

    /** Single source of time so business timestamps are testable and always UTC. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
