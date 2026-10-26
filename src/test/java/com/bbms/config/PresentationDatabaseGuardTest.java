package com.bbms.config;

import com.bbms.BbmsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PresentationDatabaseGuardTest {
    private static final String PRESENTATION_URL =
            "jdbc:mysql://127.0.0.1:3306/bbms_presentation?createDatabaseIfNotExist=false&useSSL=false";

    @Test
    void acceptsMatchingExplicitPresentationConfiguration() {
        assertDoesNotThrow(() -> PresentationDatabaseGuard.validateConfiguredIdentity(PRESENTATION_URL, "bbms_presentation"));
        assertDoesNotThrow(() -> PresentationDatabaseGuard.validateConnectedIdentity("bbms_presentation", "bbms_presentation"));
    }

    @Test
    void requiresBothUrlAndExpectedDatabase() {
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConfiguredIdentity(null, "bbms_presentation"));
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConfiguredIdentity(PRESENTATION_URL, null));
    }

    @Test
    void rejectsOperationalOrMismatchedDatabaseNames() {
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConfiguredIdentity(
                "jdbc:mysql://127.0.0.1:3306/bbms_local?createDatabaseIfNotExist=false", "bbms_local"));
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConfiguredIdentity(PRESENTATION_URL, "another_schema"));
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConnectedIdentity("bbms_presentation", "bbms_local"));
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConnectedIdentity("bbms_presentation", "unexpected_schema"));
    }

    @Test
    void requiresAutomaticDatabaseCreationToBeExplicitlyDisabled() {
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConfiguredIdentity(
                "jdbc:mysql://127.0.0.1:3306/bbms_presentation?createDatabaseIfNotExist=true", "bbms_presentation"));
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConfiguredIdentity(
                "jdbc:mysql://127.0.0.1:3306/bbms_presentation", "bbms_presentation"));
        assertThrows(IllegalStateException.class, () -> PresentationDatabaseGuard.validateConfiguredIdentity(
                "jdbc:mysql://127.0.0.1:3306/bbms_presentation?createDatabaseIfNotExist=false&createDatabaseIfNotExist=true", "bbms_presentation"));
    }

    @Test
    void presentationDatasourcePropertiesHaveNoOperationalFallback() throws IOException {
        try (var stream = getClass().getResourceAsStream("/application-presentation.properties")) {
            assertNotNull(stream);
            String properties = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(properties.contains("spring.datasource.url=${PRESENTATION_DB_URL}"));
            assertTrue(properties.contains("spring.datasource.username=${PRESENTATION_DB_USER}"));
            assertTrue(properties.contains("spring.datasource.password=${PRESENTATION_DB_PASSWORD}"));
            assertTrue(properties.contains("bbms.presentation.expected-database=${PRESENTATION_DB_NAME}"));
            assertTrue(properties.contains("spring.jpa.hibernate.ddl-auto=validate"));
        }
    }

    @Test
    void effectivePresentationConfigurationMustBeCompleteAndReadOnlyForSchema() {
        MockEnvironment environment = validPresentationEnvironment();
        assertDoesNotThrow(() -> PresentationProfileConfigurationGuard.validate(environment));

        MockEnvironment missingPassword = validPresentationEnvironment();
        missingPassword.setProperty("spring.datasource.password", "");
        assertThrows(IllegalStateException.class, () -> PresentationProfileConfigurationGuard.validate(missingPassword));

        MockEnvironment overriddenDdl = validPresentationEnvironment();
        overriddenDdl.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        assertThrows(IllegalStateException.class, () -> PresentationProfileConfigurationGuard.validate(overriddenDdl));

        MockEnvironment overriddenSqlInit = validPresentationEnvironment();
        overriddenSqlInit.setProperty("spring.sql.init.mode", "always");
        assertThrows(IllegalStateException.class, () -> PresentationProfileConfigurationGuard.validate(overriddenSqlInit));
    }

    @Test
    void explicitAdminProvisioningFlagRequiresPresentationProfileAndTrueValue() {
        MockEnvironment operational = new MockEnvironment().withProperty("provision-presentation-admin", "true");
        assertThrows(IllegalStateException.class,
                () -> PresentationProfileConfigurationGuard.validateProvisioningRequest(operational));

        MockEnvironment falseFlag = new MockEnvironment();
        falseFlag.setActiveProfiles("presentation");
        falseFlag.setProperty("provision-presentation-admin", "false");
        assertThrows(IllegalStateException.class,
                () -> PresentationProfileConfigurationGuard.validateProvisioningRequest(falseFlag));

        MockEnvironment explicit = new MockEnvironment();
        explicit.setActiveProfiles("presentation");
        explicit.setProperty("provision-presentation-admin", "true");
        assertDoesNotThrow(() -> PresentationProfileConfigurationGuard.validateProvisioningRequest(explicit));
    }

    @Test
    void earlyConfigurationGuardIsRegisteredAsAnApplicationInitializer() {
        assertTrue(SpringFactoriesLoader.loadFactories(ApplicationContextInitializer.class, getClass().getClassLoader())
                .stream().anyMatch(PresentationProfileConfigurationGuard.class::isInstance));
    }

    @Test
    void startupWriteRunnersAreDisabledForPresentationProfile() throws Exception {
        for (Method method : BbmsApplication.class.getDeclaredMethods()) {
            if (method.getReturnType() != CommandLineRunner.class) continue;
            Profile profile = method.getAnnotation(Profile.class);
            assertNotNull(profile, method.getName() + " must declare a profile");
            assertArrayEquals(new String[]{"!presentation & !training"}, profile.value(), method.getName());
        }
    }

    private MockEnvironment validPresentationEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", PRESENTATION_URL)
                .withProperty("spring.datasource.username", "presentation_user")
                .withProperty("spring.datasource.password", "configured-only-for-test")
                .withProperty("bbms.presentation.expected-database", "bbms_presentation")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("spring.liquibase.enabled", "false");
    }
}
