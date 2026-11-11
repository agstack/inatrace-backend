package com.abelium.inatrace.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ensures a PostgreSQL installation has the {@code unaccent} extension required
 * by the search normalization. MySQL installations are not affected.
 *
 * <p>The extension is created on the first start when it is missing and the
 * database user is allowed to create it. If the user does not have permission,
 * startup fails with the command that an operator has to run.
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

            if (hasUnaccent(connection)) {
                return;
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "PostgreSQL requires the unaccent extension and it could not be created automatically. "
                        + "Run \"CREATE EXTENSION IF NOT EXISTS unaccent;\" with a superuser before "
                        + "starting INATrace.",
                        e);
            }

            if (!hasUnaccent(connection)) {
                throw new IllegalStateException(
                        "PostgreSQL requires the unaccent extension. Run "
                        + "\"CREATE EXTENSION IF NOT EXISTS unaccent;\" with a superuser before starting INATrace.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not verify the database prerequisites", e);
        }
    }

    private boolean hasUnaccent(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM pg_extension WHERE extname = 'unaccent'")) {
            result.next();
            return result.getInt(1) > 0;
        }
    }
}
