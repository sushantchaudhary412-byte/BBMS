package com.bbms.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Confirms the live JDBC identity after connection creation and before any runner can write. */
@Component
@Profile("training")
public final class TrainingDatabaseGuard implements SmartInitializingSingleton {
    private static final Pattern URL_SCHEMA = Pattern.compile("^jdbc:mysql://[^/]+/([^?]+)(?:\\?.*)?$", Pattern.CASE_INSENSITIVE);
    private final DataSource dataSource;
    private final String url;
    private final String expected;

    public TrainingDatabaseGuard(DataSource dataSource,
                                 @Value("${spring.datasource.url}") String url,
                                 @Value("${bbms.training.expected-database}") String expected) {
        this.dataSource = dataSource;
        this.url = url;
        this.expected = expected;
    }

    @Override public void afterSingletonsInstantiated() {
        validateConfiguredIdentity(url, expected);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next()) throw new IllegalStateException("Training database identity query returned no result.");
            validateConnectedIdentity(expected, result.getString(1));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify the training database identity; refusing to start.");
        }
    }

    static void validateConfiguredIdentity(String jdbcUrl, String expectedDatabase) {
        if (!"bbms_training".equals(expectedDatabase)) {
            throw new IllegalStateException("Training expected database must be bbms_training.");
        }
        Matcher matcher = URL_SCHEMA.matcher(jdbcUrl == null ? "" : jdbcUrl.trim());
        if (!matcher.matches() || !"bbms_training".equals(matcher.group(1))) {
            throw new IllegalStateException("Training JDBC URL must explicitly name bbms_training.");
        }
    }

    static void validateConnectedIdentity(String expectedDatabase, String connectedDatabase) {
        if (!"bbms_training".equals(expectedDatabase) || !"bbms_training".equals(connectedDatabase)) {
            throw new IllegalStateException("Connected database is not bbms_training; refusing to start.");
        }
    }
}
