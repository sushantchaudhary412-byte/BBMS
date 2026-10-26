package com.bbms.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fail-closed configuration checks run before the datasource or Hibernate is initialized. */
public final class TrainingProfileConfigurationGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String TRAINING_DATABASE = "bbms_training";
    private static final String TRAINING_USER = "bbms_training_app";
    private static final Pattern MYSQL_SCHEMA = Pattern.compile(
            "^jdbc:mysql://[^/@]+/([A-Za-z0-9_$-]+)(?:\\?[^#]*)?$", Pattern.CASE_INSENSITIVE);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        for (String profile : environment.getActiveProfiles()) {
            if ("training".equals(profile)) {
                validate(environment);
                return;
            }
        }
    }

    static void validate(Environment environment) {
        String url = required(environment.getProperty("TRAINING_DB_URL"), "TRAINING_DB_URL");
        String expected = required(environment.getProperty("TRAINING_DB_NAME"), "TRAINING_DB_NAME");
        String user = required(environment.getProperty("TRAINING_DB_USER"), "TRAINING_DB_USER");
        String password = required(environment.getProperty("TRAINING_DB_PASSWORD"), "TRAINING_DB_PASSWORD");
        if (!TRAINING_DATABASE.equals(expected)) {
            throw new IllegalStateException("Training mode requires TRAINING_DB_NAME=bbms_training.");
        }
        Matcher matcher = MYSQL_SCHEMA.matcher(url.trim());
        if (!matcher.matches() || !TRAINING_DATABASE.equals(matcher.group(1))) {
            throw new IllegalStateException("Training mode requires a MySQL URL naming only bbms_training.");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("createdatabaseifnotexist=true") || !hasSingleExplicitCreateDisabled(url)) {
            throw new IllegalStateException("Training JDBC URL must set createDatabaseIfNotExist=false exactly once.");
        }
        if (!TRAINING_USER.equals(user) || user.equalsIgnoreCase("bbms_app") || user.equalsIgnoreCase("root")) {
            throw new IllegalStateException("Training mode requires the dedicated bbms_training_app account.");
        }
        if (password.isBlank()) throw new IllegalStateException("Training database password is required.");

        String effectiveUrl = required(environment.getProperty("spring.datasource.url"), "Training datasource URL");
        String effectiveUser = required(environment.getProperty("spring.datasource.username"), "Training datasource user");
        String effectivePassword = required(environment.getProperty("spring.datasource.password"), "Training datasource password");
        if (!url.equals(effectiveUrl) || !user.equals(effectiveUser) || !password.equals(effectivePassword)) {
            throw new IllegalStateException("Training datasource must resolve only from TRAINING_DB_* settings.");
        }
        if (!"true".equalsIgnoreCase(environment.getProperty("bbms.training.mode"))) {
            throw new IllegalStateException("Training profile requires the training data-isolation mode.");
        }
        if (!"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto", ""))
                || !"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode", ""))
                || Boolean.parseBoolean(environment.getProperty("spring.flyway.enabled", "false"))
                || Boolean.parseBoolean(environment.getProperty("spring.liquibase.enabled", "false"))) {
            throw new IllegalStateException("Training startup requires schema validation and disabled automatic initialization.");
        }
        String port = required(environment.getProperty("server.port"), "Training application port");
        if (!"8082".equals(port)) {
            throw new IllegalStateException("Training mode is isolated on port 8082; operational and presentation ports are forbidden.");
        }
        if (Boolean.parseBoolean(environment.getProperty("bbms.startup.admin-bootstrap", "false"))
                || Boolean.parseBoolean(environment.getProperty("bbms.startup.workflow-history-backfill", "false"))
                || Boolean.parseBoolean(environment.getProperty("bbms.startup.allow-depleted-inventory-migration", "false"))) {
            throw new IllegalStateException("Operational startup writers and schema migration must remain disabled in training mode.");
        }
    }

    private static String required(String value, String setting) {
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException(setting + " must be explicitly configured for training mode.");
        }
        return value.trim();
    }

    private static boolean hasSingleExplicitCreateDisabled(String url) {
        int start = url.indexOf('?');
        if (start < 0) return false;
        int count = 0;
        for (String parameter : url.substring(start + 1).split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2 && "createdatabaseifnotexist".equalsIgnoreCase(pair[0])) {
                count++;
                if (!"false".equalsIgnoreCase(pair[1])) return false;
            }
        }
        return count == 1;
    }
}
