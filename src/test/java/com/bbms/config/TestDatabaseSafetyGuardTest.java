package com.bbms.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class TestDatabaseSafetyGuardTest {
    @Test
    void acceptsOnlyTheExplicitDisposableInMemoryDatasource() {
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.validate(safeEnvironment()));
    }

    @Test
    void rejectsOperationalAndPresentationDatabasesBeforeContextStartup() {
        MockEnvironment operational = safeEnvironment();
        operational.setProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/bbms_local");
        assertThrows(IllegalStateException.class, () -> TestDatabaseSafetyGuard.validate(operational));

        MockEnvironment presentation = safeEnvironment();
        presentation.setProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/bbms_presentation");
        assertThrows(IllegalStateException.class, () -> TestDatabaseSafetyGuard.validate(presentation));

        MockEnvironment training = safeEnvironment();
        training.setProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/bbms_training");
        assertThrows(IllegalStateException.class, () -> TestDatabaseSafetyGuard.validate(training));
    }

    @Test
    void rejectsUnknownDatabaseAndUnsafeSchemaSettings() {
        MockEnvironment unknown = safeEnvironment();
        unknown.setProperty("spring.datasource.url", "jdbc:h2:mem:other");
        assertThrows(IllegalStateException.class, () -> TestDatabaseSafetyGuard.validate(unknown));

        MockEnvironment schemaUpdate = safeEnvironment();
        schemaUpdate.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        assertThrows(IllegalStateException.class, () -> TestDatabaseSafetyGuard.validate(schemaUpdate));
    }

    private MockEnvironment safeEnvironment() {
        return new MockEnvironment().withProperty("spring.datasource.url", TestDatabaseSafetyGuard.SAFE_URL)
                .withProperty("spring.datasource.driver-class-name", "org.h2.Driver")
                .withProperty("spring.datasource.username", "sa")
                .withProperty("spring.jpa.hibernate.ddl-auto", "create-drop")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("spring.profiles.active", "test");
    }
}
