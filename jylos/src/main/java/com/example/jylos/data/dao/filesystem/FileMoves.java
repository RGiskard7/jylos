package com.example.jylos.data.dao.filesystem;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Moves a folder or file, trying again for a moment when the system refuses it because another
 * program has it open. Windows does that while an antivirus, the search indexer or a sync
 * client looks at a file just written; it lets go within a fraction of a second. A move that
 * cannot work (the target exists, the source is gone) fails at once.
 */
final class FileMoves {

    /** How many times a move is tried, a short wait apart (half a second in all). */
    static final int ATTEMPTS = 10;
    private static final long WAIT_MILLIS = 50;

    /** The move itself, {@link Files#move} outside tests. */
    @FunctionalInterface
    interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    /** The wait between two tries, {@link Thread#sleep} outside tests. */
    @FunctionalInterface
    interface Waiter {
        void waitFor(long millis) throws InterruptedException;
    }

    private FileMoves() {
    }

    static void move(Path from, Path to) throws IOException {
        move(from, to, (source, target) -> Files.move(source, target), Thread::sleep);
    }

    static void move(Path from, Path to, Mover mover, Waiter waiter) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                mover.move(from, to);
                return;
            } catch (FileSystemException e) {
                if (!inUseForAMoment(e) || attempt >= ATTEMPTS) {
                    throw e;
                }
            }
            try {
                waiter.waitFor(WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while moving " + from, e);
            }
        }
    }

    /**
     * Refused because something has it open: access denied, or a plain file system error (the
     * file in use). Its subclasses say why the move cannot work: the target exists, the source
     * is gone, the folder is not empty.
     */
    private static boolean inUseForAMoment(FileSystemException e) {
        return e instanceof AccessDeniedException || e.getClass() == FileSystemException.class;
    }
}
