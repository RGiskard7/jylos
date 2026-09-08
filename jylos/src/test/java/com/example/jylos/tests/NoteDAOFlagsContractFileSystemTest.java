package com.example.jylos.tests;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.interfaces.NoteDAO;

/** {@link NoteDAOFlagsContractTest} sobre {@link NoteDAOFileSystem}, con un vault real y temporal en disco. */
class NoteDAOFlagsContractFileSystemTest extends NoteDAOFlagsContractTest {

    @TempDir
    Path tempDir;

    private NoteDAOFileSystem noteDAO;

    @BeforeEach
    void setUp() {
        noteDAO = new NoteDAOFileSystem(tempDir.toString());
    }

    @Override
    protected NoteDAO createNoteDAO() {
        return noteDAO;
    }
}
