package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.database.SQLiteDB;
import com.example.jylos.exceptions.DataAccessException;

/**
 * A DAO write is one transaction: what it wrote is saved for good (another connection sees
 * it), and when one of its statements fails nothing of it stays, and the failure is reported.
 */
class SQLiteTransactionTest {

    @TempDir
    Path tempDir;

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("tx.db"));
        connection = SQLiteTestSupport.openConnection();
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS probe (value TEXT)");
        }
        connection.commit();
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void whatATransactionWritesIsSavedForGood() throws Exception {
        SQLiteDB.inTransaction(connection, "insert", () -> insert("uno"));

        try (Connection other = SQLiteTestSupport.openConnection()) {
            assertEquals(1, count(other), "another connection sees it");
        }
    }

    @Test
    void aFailedTransactionLeavesNothingAndIsReported() throws Exception {
        DataAccessException error = assertThrows(DataAccessException.class,
                () -> SQLiteDB.inTransaction(connection, "Could not write the probe", () -> {
                    insert("uno");
                    throw new SQLException("the second statement fails");
                }));

        assertEquals("Could not write the probe", error.getMessage());
        assertEquals(0, count(connection), "the first statement is undone too");
    }

    private void insert(String value) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO probe (value) VALUES ('" + value + "')");
        }
    }

    private static int count(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM probe")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}
