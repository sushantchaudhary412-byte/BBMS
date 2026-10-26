package com.bbms.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Reminder scheduling is enabled by default for normal application runs. It can
 * be disabled explicitly for read-only diagnostics and maintenance sessions.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bbms.scheduling.reminders-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderSchedulingConfiguration {
}
