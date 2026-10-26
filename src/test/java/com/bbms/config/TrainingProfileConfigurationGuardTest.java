package com.bbms.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class TrainingProfileConfigurationGuardTest {
    @Test void acceptsOnlyTheDedicatedTrainingDatasourceAnd8082() {
        assertDoesNotThrow(() -> TrainingProfileConfigurationGuard.validate(valid()));
    }

    @Test void refusesOperationalAndPresentationDatabaseUrls() {
        for (String schema : new String[]{"bbms_local", "bbms_presentation"}) {
            MockEnvironment env=valid();
            env.setProperty("TRAINING_DB_URL", "jdbc:mysql://127.0.0.1:3306/"+schema+"?createDatabaseIfNotExist=false");
            env.setProperty("spring.datasource.url", env.getProperty("TRAINING_DB_URL"));
            assertThrows(IllegalStateException.class, () -> TrainingProfileConfigurationGuard.validate(env));
        }
    }

    @Test void refusesPortOverridesAndOperationalFallbacks() {
        MockEnvironment wrongPort=valid(); wrongPort.setProperty("server.port", "8080");
        assertThrows(IllegalStateException.class, () -> TrainingProfileConfigurationGuard.validate(wrongPort));
        MockEnvironment fallback=valid(); fallback.setProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/bbms_local");
        assertThrows(IllegalStateException.class, () -> TrainingProfileConfigurationGuard.validate(fallback));
    }

    private MockEnvironment valid() {
        String url="jdbc:mysql://127.0.0.1:3306/bbms_training?createDatabaseIfNotExist=false&useSSL=false";
        return new MockEnvironment()
                .withProperty("TRAINING_DB_NAME","bbms_training")
                .withProperty("TRAINING_DB_URL",url)
                .withProperty("TRAINING_DB_USER","bbms_training_app")
                .withProperty("TRAINING_DB_PASSWORD","test-only-secret")
                .withProperty("spring.datasource.url",url)
                .withProperty("spring.datasource.username","bbms_training_app")
                .withProperty("spring.datasource.password","test-only-secret")
                .withProperty("bbms.training.mode","true")
                .withProperty("spring.jpa.hibernate.ddl-auto","validate")
                .withProperty("spring.sql.init.mode","never")
                .withProperty("spring.flyway.enabled","false")
                .withProperty("spring.liquibase.enabled","false")
                .withProperty("server.port","8082")
                .withProperty("bbms.startup.admin-bootstrap","false")
                .withProperty("bbms.startup.workflow-history-backfill","false")
                .withProperty("bbms.startup.allow-depleted-inventory-migration","false");
    }
}
