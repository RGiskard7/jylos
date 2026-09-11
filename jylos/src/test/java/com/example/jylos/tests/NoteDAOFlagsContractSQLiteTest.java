package com.example.jylos.tests;

import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;

/** {@link NoteDAOFlagsContractTest} sobre {@link NoteDAOSQLite}, con BD SQLite real y temporal. */
class NoteDAOFlagsContractSQLiteTest extends NoteDAOFlagsContractTest {

    @TempDir
    Path tempDir;

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("contract-flags.db");
        SQLiteTestSupport.configureFreshDatabase(dbFile);
        connection = SQLiteTestSupport.openConnection();
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Override
    protected NoteDAO createNoteDAO() {
        return new NoteDAOSQLite(connection);
    }
}
