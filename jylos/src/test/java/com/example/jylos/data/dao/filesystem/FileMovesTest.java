package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Windows refuses for a moment to move a folder or file that another program has just opened
 * (an antivirus, the search indexer, a sync client): the move is tried again a few times before
 * it fails. A move that cannot work (the target exists, the source is gone) fails at once.
 */
class FileMovesTest {

    private static final Path FROM = Path.of("Trabajo");
    private static final Path TO = Path.of("Oficina");

    private final List<String> tries = new ArrayList<>();
    private final List<Long> waits = new ArrayList<>();

    /** A move that is refused {@code refusals} times with {@code refusal}, then works. */
    private FileMoves.Mover refusing(int refusals, IOException refusal) {
        return (from, to) -> {
            tries.add(from + "->" + to);
            if (tries.size() <= refusals) {
                throw refusal;
            }
        };
    }

    @Test
    void aMoveRefusedForAMomentIsTriedAgain() throws Exception {
        FileMoves.move(FROM, TO, refusing(2, new AccessDeniedException("Trabajo")), waits::add);

        assertEquals(List.of("Trabajo->Oficina", "Trabajo->Oficina", "Trabajo->Oficina"), tries);
        assertEquals(2, waits.size(), "a short wait before each new try");
    }

    @Test
    void aFileInUseByAnotherProgramIsTriedAgainToo() throws Exception {
        FileMoves.move(FROM, TO, refusing(1, new FileSystemException("Trabajo", null,
                "The process cannot access the file because it is being used by another process")), waits::add);

        assertEquals(2, tries.size());
    }

    @Test
    void aMoveStillRefusedFailsWithItsReason() {
        AccessDeniedException refusal = new AccessDeniedException("Trabajo");

        AccessDeniedException thrown = assertThrows(AccessDeniedException.class,
                () -> FileMoves.move(FROM, TO, refusing(Integer.MAX_VALUE, refusal), waits::add));

        assertEquals(refusal, thrown);
        assertEquals(FileMoves.ATTEMPTS, tries.size());
    }

    @Test
    void aMoveThatCannotWorkFailsAtOnce() {
        assertThrows(FileAlreadyExistsException.class, () -> FileMoves.move(FROM, TO,
                refusing(Integer.MAX_VALUE, new FileAlreadyExistsException("Oficina")), waits::add));

        assertEquals(1, tries.size());
        assertEquals(List.of(), waits);
    }
}
