package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.service.DatabaseBackupService;

class DatabaseBackupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void backupDatabaseFileCreatesConsistentCopy() throws Exception {
        File source = tempDir.resolve("source.db").toFile();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source.getAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample (id INTEGER PRIMARY KEY, value TEXT)");
            statement.execute("INSERT INTO sample(value) VALUES ('ok')");
        }

        File target = tempDir.resolve("backup.db").toFile();
        assertTrue(DatabaseBackupService.backupDatabaseFile(source, target));
        assertTrue(target.isFile());
        assertTrue(target.length() > 0);
    }
}
