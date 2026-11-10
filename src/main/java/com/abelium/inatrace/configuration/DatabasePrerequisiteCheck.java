package com.abelium.inatrace.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Fails fast when a PostgreSQL installation is missing the {@code unaccent}
 * extension required by the search normalization. MySQL installations are not
 * affected.
 */
@Component
public class DatabasePrerequisiteCheck {

    private final DataSource dataSource;

    public DatabasePrerequisiteCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void check() {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM pg_extension WHERE extname = 'unaccent'")) {
                result.next();
                if (result.getInt(1) == 0) {
                    throw new IllegalStateException(
                            "PostgreSQL requires the unaccent extension. Run "
                            + "\"CREATE EXTENSION IF NOT EXISTS unaccent;\" with a superuser before starting INATrace.");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not verify the database prerequisites", e);
        }
    }
}
