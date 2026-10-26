package com.bbms.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fails closed unless the presentation profile is connected to its explicitly named schema. */
@Component
@Profile("presentation")
public class PresentationDatabaseGuard implements SmartInitializingSingleton {
    private static final String OPERATIONAL_DATABASE = "bbms_local";
    private static final Pattern MYSQL_SCHEMA = Pattern.compile("^jdbc:mysql://[^/]+/([^?;]+)(?:\\?.*)?$", Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final String expectedDatabase;

    public PresentationDatabaseGuard(
            DataSource dataSource,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${bbms.presentation.expected-database}") String expectedDatabase) {
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl;
        this.expectedDatabase = expectedDatabase;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validateConfiguredIdentity(jdbcUrl, expectedDatabase);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next()) {
                throw new IllegalStateException("Presentation database identity query returned no result; refusing to start.");
            }
            validateConnectedIdentity(expectedDatabase, result.getString(1));
        } catch (SQLException ex) {
            // Deliberately omit the exception cause: JDBC diagnostics can contain connection details.
            throw new IllegalStateException("Unable to verify the presentation database identity; refusing to start.");
        }
    }

    static void validateConfiguredIdentity(String jdbcUrl, String expectedDatabase) {
        String expected = requireDatabaseName(expectedDatabase, "An explicit presentation database name is required.");
        if (OPERATIONAL_DATABASE.equalsIgnoreCase(expected)) {
            throw new IllegalStateException("The operational database cannot be used in presentation mode.");
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("An explicit presentation JDBC URL is required.");
        }
        Matcher matcher = MYSQL_SCHEMA.matcher(jdbcUrl.trim());
        if (!matcher.matches()) {
            throw new IllegalStateException("Presentation mode requires a MySQL JDBC URL with an explicit database name.");
        }
        String configuredDatabase = requireDatabaseName(matcher.group(1), "The presentation JDBC URL must name a database.");
        if (OPERATIONAL_DATABASE.equalsIgnoreCase(configuredDatabase)) {
            throw new IllegalStateException("The operational database cannot be used in presentation mode.");
        }
        if (!expected.equals(configuredDatabase)) {
            throw new IllegalStateException("The presentation JDBC database does not match the explicitly expected database.");
        }
        if (!hasExplicitDatabaseCreationDisabled(jdbcUrl)) {
            throw new IllegalStateException("The presentation JDBC URL must explicitly set createDatabaseIfNotExist=false.");
        }
    }

    static void validateConnectedIdentity(String expectedDatabase, String connectedDatabase) {
        String expected = requireDatabaseName(expectedDatabase, "An explicit presentation database name is required.");
        String actual = requireDatabaseName(connectedDatabase, "The connected database identity is missing; refusing to start.");
        if (OPERATIONAL_DATABASE.equalsIgnoreCase(actual) || !expected.equals(actual)) {
            throw new IllegalStateException("The connected database is not the explicitly expected presentation database.");
        }
    }

    private static String requireDatabaseName(String name, String message) {
        if (name == null || !name.matches("[A-Za-z0-9_$-]{1,64}")) {
            throw new IllegalStateException(message);
        }
        return name;
    }

    private static boolean hasExplicitDatabaseCreationDisabled(String jdbcUrl) {
        int queryStart = jdbcUrl.indexOf('?');
        if (queryStart < 0) return false;
        int occurrences = 0;
        for (String parameter : jdbcUrl.substring(queryStart + 1).split("&")) {
            String[] parts = parameter.split("=", 2);
            if (parts.length == 2 && "createdatabaseifnotexist".equals(parts[0].toLowerCase(Locale.ROOT))) {
                occurrences++;
                if (!"false".equalsIgnoreCase(parts[1])) return false;
            }
        }
        return occurrences == 1;
    }
}
