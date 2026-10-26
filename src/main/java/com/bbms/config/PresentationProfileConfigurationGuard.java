package com.bbms.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Validates presentation configuration before Hibernate or startup runners are initialized. */
public class PresentationProfileConfigurationGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        validateProvisioningRequest(environment);
        for (String profile : environment.getActiveProfiles()) {
            if ("presentation".equals(profile)) {
                validate(environment);
                return;
            }
        }
    }

    static void validateProvisioningRequest(Environment environment) {
        String request = environment.getProperty("provision-presentation-admin");
        if (request == null) return;
        boolean presentationProfile = false;
        for (String profile : environment.getActiveProfiles()) {
            if ("presentation".equals(profile)) presentationProfile = true;
        }
        if (!presentationProfile) {
            throw new IllegalStateException("Presentation Admin provisioning requires the presentation profile.");
        }
        if (!"true".equalsIgnoreCase(request)) {
            throw new IllegalStateException("Presentation Admin provisioning must be explicitly enabled with --provision-presentation-admin=true.");
        }
    }

    static void validate(Environment environment) {
        String jdbcUrl = required(environment.getProperty("spring.datasource.url"), "Presentation JDBC URL");
        String expectedDatabase = required(environment.getProperty("bbms.presentation.expected-database"), "Expected presentation database name");
        required(environment.getProperty("spring.datasource.username"), "Presentation database username");
        required(environment.getProperty("spring.datasource.password"), "Presentation database password");
        PresentationDatabaseGuard.validateConfiguredIdentity(jdbcUrl, expectedDatabase);

        if (!"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto", ""))) {
            throw new IllegalStateException("Presentation mode requires Hibernate ddl-auto=validate before application startup.");
        }
        if (!"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode", ""))) {
            throw new IllegalStateException("Presentation mode requires SQL initialization to be disabled.");
        }
        if (Boolean.parseBoolean(environment.getProperty("spring.flyway.enabled", "false"))
                || Boolean.parseBoolean(environment.getProperty("spring.liquibase.enabled", "false"))) {
            throw new IllegalStateException("Automatic schema migrations are disabled in presentation mode until explicitly reviewed.");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException(label + " must be explicitly configured for presentation mode.");
        }
        return value;
    }
}
