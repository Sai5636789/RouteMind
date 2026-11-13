package com.routemind.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A Clock bean so time-dependent components (the GPS simulator) can
 * take a fixed clock in tests instead of calling Instant.now()
 * directly. Production gets the system clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
