package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SQLiteDBIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void openConnectionEnablesForeignKeysPragma() throws Exception {
        Path dbFile = tempDir.resolve("sqlite-hardening-test.db");
        SQLiteTestSupport.configureFreshDatabase(dbFile);

        Connection connection = SQLiteTestSupport.openConnection();
        try (Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
                assertEquals(1, resultSet.getInt(1));
            }
            try (ResultSet journalMode = statement.executeQuery("PRAGMA journal_mode")) {
                assertEquals("wal", journalMode.getString(1).toLowerCase());
            }
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }
}
