package com.bbms.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;

/** Fails before Hibernate or test code can write unless an in-memory test DB is selected. */
public final class TestDatabaseSafetyGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final String SAFE_URL =
            "jdbc:h2:mem:bbms_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }

    static void validate(Environment environment) {
        String url = environment.getProperty("spring.datasource.url", "");
        String normalized = url.toLowerCase(Locale.ROOT);
        if (normalized.contains("bbms_local") || normalized.contains("bbms_presentation") || normalized.contains("bbms_training")) {
            throw new IllegalStateException("Integration tests must never connect to a protected BBMS database.");
        }
        if (!Arrays.asList(environment.getActiveProfiles()).contains("test") || !SAFE_URL.equals(url)) {
            throw new IllegalStateException("Integration tests require the designated in-memory bbms_test database.");
        }
        if (!"org.h2.Driver".equals(environment.getProperty("spring.datasource.driver-class-name"))
                || !"sa".equals(environment.getProperty("spring.datasource.username"))) {
            throw new IllegalStateException("Integration tests require the isolated H2 test datasource.");
        }
        if (!"create-drop".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto", ""))
                || !"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode", ""))) {
            throw new IllegalStateException("Integration tests require disposable schema creation and disabled SQL initialization.");
        }
    }
}
