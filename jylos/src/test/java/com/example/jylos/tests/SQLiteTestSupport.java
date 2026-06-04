package com.example.jylos.tests;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;

import com.example.jylos.data.database.SQLiteDB;

/**
 * Shared helpers for integration tests against a real on-disk SQLite database.
 */
final class SQLiteTestSupport {

    private SQLiteTestSupport() {
    }

    static void configureFreshDatabase(Path dbFile) throws Exception {
        resetSingleton();
        SQLiteDB.configure(dbFile.toString());
        SQLiteDB.getInstance().initDatabase();
    }

    static Connection openConnection() {
        return SQLiteDB.getInstance().openConnection();
    }

    static void closeAndReset(Connection connection) throws Exception {
        if (connection != null) {
            SQLiteDB.getInstance().closeConnection(connection);
        }
        resetSingleton();
    }

    static void resetSingleton() throws Exception {
        Field instanceField = SQLiteDB.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
