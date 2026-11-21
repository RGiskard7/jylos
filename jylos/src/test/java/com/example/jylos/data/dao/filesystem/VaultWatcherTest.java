package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The watcher reports, in batches, the paths changed under the vault by anyone (FSEvents on
 * macOS, the JDK elsewhere), under the vault path as the app knows it, leaving out hidden
 * folders and temporary files; once closed it reports nothing.
 */
class VaultWatcherTest {

    private static final long WAIT_MS = 15_000;

    @TempDir
    Path vault;

    @Test
    void aFileWrittenOutsideIsReported() throws Exception {
        List<Set<Path>> batches = new CopyOnWriteArrayList<>();
        try (VaultWatcher watcher = VaultWatcher.start(vault, batches::add, () -> { })) {
            Path note = vault.resolve("Nueva.md");
            Files.writeString(note, "fuera\n");

            assertTrue(waitFor(() -> reported(batches).contains(note)), "reported: " + batches);
        }
    }

    @Test
    void aBurstIsReportedTogetherAndHiddenOrTemporaryPathsAreLeftOut() throws Exception {
        List<Set<Path>> batches = new CopyOnWriteArrayList<>();
        AtomicInteger lost = new AtomicInteger();
        Files.createDirectories(vault.resolve(".obsidian"));
        Files.createDirectories(vault.resolve("Carpeta"));
        try (VaultWatcher watcher = VaultWatcher.start(vault, batches::add, lost::incrementAndGet)) {
            Thread.sleep(VaultWatcher.RECONCILE_AFTER_MS + 500); // listening, its first check done
            batches.clear();
            Set<Path> written = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                Path note = vault.resolve("Carpeta/Nota " + i + ".md");
                Files.writeString(note, "nota " + i + "\n");
                written.add(note);
            }
            Files.writeString(vault.resolve(".obsidian/workspace.json"), "{}");
            Files.writeString(vault.resolve("Carpeta/Nota 0.md123.tmp"), "x");

            assertTrue(waitFor(() -> lost.get() > 0 || reported(batches).containsAll(written)),
                    "reported " + reported(batches).size() + " of 200");
            assertTrue(batches.size() <= 10, batches.size() + " batches for one burst");
            Thread.sleep(1500);
            assertFalse(reported(batches).stream().anyMatch(p -> p.toString().contains(".obsidian")
                    || p.toString().endsWith(".tmp")), "hidden or temporary: " + reported(batches));
        }
    }

    /**
     * The library takes its inventory and starts listening on another thread a moment later:
     * notes written in between still end up listed, through the check of the whole vault it
     * asks for once it is listening. (The vault keeps a note, so the list is never empty: an
     * empty list is read again whole, which would hide what this checks.)
     */
    @Test
    void everyNoteEndsUpListedEvenIfWrittenRightAsTheWatcherStarts() throws Exception {
        Files.writeString(vault.resolve("Fija.md"), "fija\n");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        try (VaultWatcher watcher = VaultWatcher.start(vault, dao::applyExternalChanges, dao::refreshCache)) {
            for (int i = 0; i < 200; i++) {
                Files.writeString(vault.resolve("Nota " + i + ".md"), "nota " + i + "\n");
            }

            assertTrue(waitFor(() -> dao.fetchAllNotes().size() == 201), dao.fetchAllNotes().size() + " listed");
        }
    }

    @Test
    void onceClosedNothingIsReported() throws Exception {
        List<Set<Path>> batches = new CopyOnWriteArrayList<>();
        VaultWatcher watcher = VaultWatcher.start(vault, batches::add, () -> { });
        watcher.close();

        Files.writeString(vault.resolve("Tarde.md"), "tarde\n");
        Thread.sleep(2500);

        assertEquals(List.of(), batches);
        assertFalse(Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.isAlive() && t.getName().startsWith("vault-watcher")), "its threads ended");
    }

    private static Set<Path> reported(List<Set<Path>> batches) {
        Set<Path> all = new HashSet<>();
        batches.forEach(all::addAll);
        return all;
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(50);
        }
        return true;
    }
}
