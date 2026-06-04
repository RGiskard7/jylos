package com.example.jylos.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Logger;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.config.LoggerConfig;

/**
 * Creates timestamped SQLite copies on startup and prunes old files.
 * Backup files live under {@link AppDataDirectory#getBackupsDirectory()} (gitignored).
 */
public final class DatabaseBackupService {

    private static final Logger LOGGER = LoggerConfig.getLogger(DatabaseBackupService.class);
    private static final String BACKUP_PREFIX = "database-auto-backup-";
    private static final String BACKUP_SUFFIX = ".db";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int MAX_RETAINED_BACKUPS = 5;

    private DatabaseBackupService() {
    }

    /**
     * Copies {@code data/database.db} into {@code backups/} if the source exists.
     * Keeps at most {@value #MAX_RETAINED_BACKUPS} backup files (oldest removed first).
     */
    public static void createStartupBackupIfNeeded() {
        File sourceDb = new File(AppDataDirectory.getDataDirectory(), "database.db");
        if (!sourceDb.isFile()) {
            return;
        }

        File backupsDir = new File(AppDataDirectory.getBackupsDirectory());
        if (!backupsDir.exists() && !backupsDir.mkdirs()) {
            LOGGER.warning("Cannot create backups directory: " + backupsDir.getAbsolutePath());
            return;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        File targetDb = new File(backupsDir, BACKUP_PREFIX + timestamp + BACKUP_SUFFIX);

        if (backupDatabaseFile(sourceDb, targetDb)) {
            LOGGER.info("Automatic backup created: " + targetDb.getName());
            pruneOldBackups(backupsDir);
        } else {
            LOGGER.warning("Failed to perform automatic backup for " + sourceDb.getAbsolutePath());
        }
    }

    /**
     * Creates a consistent SQLite backup using {@code VACUUM INTO} when possible.
     * Falls back to file copy only when the database is not locked by another process.
     */
    public static boolean backupDatabaseFile(File sourceDb, File targetDb) {
        if (sourceDb == null || targetDb == null || !sourceDb.isFile()) {
            return false;
        }
        File parent = targetDb.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        if (targetDb.exists() && !targetDb.delete()) {
            return false;
        }

        if (vacuumInto(sourceDb, targetDb)) {
            return targetDb.isFile();
        }
        return copyDatabaseFile(sourceDb, targetDb);
    }

    private static boolean vacuumInto(File sourceDb, File targetDb) {
        String jdbcUrl = "jdbc:sqlite:" + sourceDb.getAbsolutePath();
        String escapedTarget = targetDb.getAbsolutePath().replace("'", "''");
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + escapedTarget + "'");
            return true;
        } catch (SQLException e) {
            LOGGER.fine("VACUUM INTO backup failed, trying file copy: " + e.getMessage());
            return false;
        }
    }

    private static boolean copyDatabaseFile(File sourceDb, File targetDb) {
        try {
            Files.copy(sourceDb.toPath(), targetDb.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOGGER.warning("File copy backup failed: " + e.getMessage());
            return false;
        }
    }

    private static void pruneOldBackups(File backupsDir) {
        File[] backups = backupsDir.listFiles((dir, name) ->
                name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX));
        if (backups == null || backups.length <= MAX_RETAINED_BACKUPS) {
            return;
        }
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
        int toDelete = backups.length - MAX_RETAINED_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            if (backups[i].delete()) {
                LOGGER.info("Deleted old backup: " + backups[i].getName());
            }
        }
    }
}
