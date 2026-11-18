package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;

/**
 * The command that cleans a vault of the fields older versions of Jylos wrote: it says how
 * many notes it would touch and asks first; cancelling touches nothing; accepting copies each
 * note to {@code .jylos/backups/} and cleans it, and says how many and where the copies are.
 * With nothing to clean it says so, and outside a folder vault it does nothing.
 */
class VaultCleanupSupportTest {

    private static final String LEGACY = "---\nid: X.md\ntitle: X\nfavorite: false\npinned: false\ndeleted: false\n"
            + "prioridad: 3\n---\ntexto\n";

    @TempDir
    Path vault;

    private final List<String> status = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();

    private Runnable command(NoteService notes, boolean accept) {
        VaultCleanupSupport support = new VaultCleanupSupport();
        Map<ActionType, Runnable> handlers = new EnumMap<>(ActionType.class);
        support.register(handlers, () -> notes, key -> key, status::add, () -> null);
        support.useForTests(Runnable::run, Runnable::run, (count, backups) -> {
            asked.add(count + " " + vault.relativize(backups).toString().replaceAll("\\d", "N"));
            return accept;
        });
        return handlers.getOrDefault(ActionType.VAULT_CLEANUP, () -> status.add("no command"));
    }

    private NoteService vaultNotes() {
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    private void write(String name, String text) throws Exception {
        Files.writeString(vault.resolve(name), text, StandardCharsets.UTF_8);
    }

    @Test
    void cancellingTouchesNothing() throws Exception {
        write("A.md", LEGACY);
        write("B.md", LEGACY);

        command(vaultNotes(), false).run();

        assertEquals(List.of("2 .jylos/backups/cleanup-NNNNNNNN-NNNNNN"), asked);
        assertEquals(List.of(LEGACY, LEGACY), List.of(Files.readString(vault.resolve("A.md")),
                Files.readString(vault.resolve("B.md"))));
        assertTrue(Files.notExists(vault.resolve(".jylos/backups")), "no copies when cancelled");
        assertEquals("cleanup.status.cancelled", status.get(status.size() - 1));
    }

    @Test
    void acceptingCopiesEachNoteAndCleansIt() throws Exception {
        write("A.md", LEGACY);
        write("B.md", LEGACY);
        write("Obsidian.md", "---\ntags: [a]\n---\ntexto\n");

        command(vaultNotes(), true).run();

        assertEquals(List.of("2 .jylos/backups/cleanup-NNNNNNNN-NNNNNN"), asked);
        assertEquals("---\nprioridad: 3\n---\ntexto\n", Files.readString(vault.resolve("A.md")));
        try (var copies = Files.list(vault.resolve(".jylos/backups"))) {
            Path dir = copies.findFirst().orElseThrow();
            assertEquals(List.of(LEGACY, LEGACY), List.of(Files.readString(dir.resolve("A.md")),
                    Files.readString(dir.resolve("B.md"))));
        }
        assertEquals("cleanup.status.done", status.get(status.size() - 1));
    }

    @Test
    void withNothingToCleanItSaysSoAndOutsideAVaultItDoesNothing() throws Exception {
        write("Obsidian.md", "---\ntags: [a]\n---\ntexto\n");

        command(vaultNotes(), true).run();
        command(null, true).run();

        assertEquals(List.of(), asked);
        assertEquals("cleanup.status.none", status.get(status.size() - 2));
        assertEquals("cleanup.status.only_vault", status.get(status.size() - 1));
    }
}
