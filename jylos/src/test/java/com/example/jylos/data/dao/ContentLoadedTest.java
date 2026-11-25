package com.example.jylos.data.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;

/**
 * A storage that loads everything at once (the database) has its content loaded from the
 * start, so what waits for it runs at once, exactly once, as with a folder vault once its
 * background load ends. What the app starts then (showing changes written by an MCP client,
 * among others) would otherwise never start with the database.
 */
class ContentLoadedTest {

    @Test
    void aStorageThatLoadsAtOnceRunsWhatWaitsForItsContentAtOnce() {
        AtomicInteger runs = new AtomicInteger();

        new NoteDAOSQLite(null).setOnContentLoaded(runs::incrementAndGet);

        assertEquals(1, runs.get());
    }

    @Test
    void clearingWhatWaitsIsNotAnError() {
        assertDoesNotThrow(() -> new NoteDAOSQLite(null).setOnContentLoaded(null));
    }
}
