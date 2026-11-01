package com.example.jylos.data.dao.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import com.example.jylos.data.dao.interfaces.EncryptionKeyDAO;
import com.example.jylos.data.models.KeyMaterial;
import com.example.jylos.exceptions.DataAccessException;

/**
 * Keeps the private-notes key material in the SQLite database itself (table
 * {@code app_settings}), so a copied or restored database can still be unlocked.
 */
public class EncryptionKeyDAOSQLite implements EncryptionKeyDAO {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)";
    private static final String SELECT = "SELECT value FROM app_settings WHERE key = ?";
    private static final String UPSERT =
            "INSERT INTO app_settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value";
    private static final String SALT = "encryption.salt";
    private static final String VERIFIER = "encryption.verifier";
    private static final String ITERATIONS = "encryption.iterations";

    private final Connection connection;

    public EncryptionKeyDAOSQLite(Connection connection) {
        this.connection = connection;
        synchronized (connection) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_TABLE);
                commitIfNeeded();
            } catch (SQLException e) {
                throw new DataAccessException("Could not prepare encryption settings table", e);
            }
        }
    }

    @Override
    public Optional<KeyMaterial> load() {
        synchronized (connection) {
            try {
                String salt = read(SALT);
                String verifier = read(VERIFIER);
                if (salt == null || verifier == null) {
                    return Optional.empty();
                }
                String iterations = read(ITERATIONS);
                return Optional.of(new KeyMaterial(salt, verifier, iterations != null ? Integer.parseInt(iterations) : 0));
            } catch (SQLException | NumberFormatException e) {
                throw new DataAccessException("Unreadable encryption settings", e);
            }
        }
    }

    @Override
    public void save(KeyMaterial material) {
        synchronized (connection) {
            try {
                write(SALT, material.salt());
                write(VERIFIER, material.verifier());
                write(ITERATIONS, Integer.toString(material.iterations()));
                commitIfNeeded();
            } catch (SQLException e) {
                rollbackQuietly();
                throw new DataAccessException("Could not write encryption settings", e);
            }
        }
    }

    private String read(String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void write(String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void commitIfNeeded() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    private void rollbackQuietly() {
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException ignored) {
            // the original failure is what gets reported
        }
    }
}
