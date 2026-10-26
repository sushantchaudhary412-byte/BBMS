package com.bbms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TrainingClockConfiguration {
    @Bean @Primary @Profile("training")
    TrainingClock trainingClock() { return new TrainingClock(ZoneId.systemDefault()); }

    @Bean @Primary @Profile("!training")
    Clock systemClock() { return Clock.systemDefaultZone(); }
}
