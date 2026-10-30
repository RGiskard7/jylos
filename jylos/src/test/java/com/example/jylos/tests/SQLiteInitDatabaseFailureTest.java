package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.database.SQLiteDB;
import com.example.jylos.exceptions.DataAccessException;

/**
 * If the database cannot be brought to the schema the app needs, startup must fail loudly.
 * Swallowing the error let the app open on a broken schema, where every later query
 * failed quietly and the user saw an empty app with no explanation.
 */
class SQLiteInitDatabaseFailureTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() throws Exception {
        SQLiteTestSupport.resetSingleton();
    }

    @Test
    void aSchemaThatCannotBeCompletedIsReportedNotSwallowed() throws Exception {
        Path db = tempDir.resolve("broken.db");
        // A notes table from some unrelated or damaged database: no parent_id column, so the
        // required indexes cannot be created on it.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE notes (note_id TEXT PRIMARY KEY, title TEXT)");
        }
        SQLiteTestSupport.resetSingleton();
        SQLiteDB.configure(db.toString());

        assertThrows(DataAccessException.class, () -> SQLiteDB.getInstance().initDatabase());
    }

    @Test
    void aFreshDatabaseInitialisesWithoutErrors() throws Exception {
        SQLiteTestSupport.resetSingleton();
        SQLiteDB.configure(tempDir.resolve("fresh.db").toString());
        assertDoesNotThrow(() -> SQLiteDB.getInstance().initDatabase());
        // and a second start on the same file is still fine (idempotent migrations)
        assertDoesNotThrow(() -> SQLiteDB.getInstance().initDatabase());
    }
}
