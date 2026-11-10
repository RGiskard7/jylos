package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/**
 * The shared note menu, independent of the machine's storage preference: "Reveal in files"
 * only for a folder vault, and no trailing separator when no plugin adds entries.
 */
class NoteContextMenuSupportTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void aFolderVaultOffersRevealInFilesAndItRevealsTheNote() throws Exception {
        onFxThread(() -> aFolderVaultOffersRevealInFilesAndItRevealsTheNoteBody());
    }

    private void aFolderVaultOffersRevealInFilesAndItRevealsTheNoteBody() {
        ContextMenu menu = NoteContextMenuSupport.create(note("a"), actions(), true, key -> key, List.of());

        assertEquals(List.of("action.open", "action.open_in_new_tab", "action.pin_note", "action.add_favorite",
                "action.make_private", "action.rename", "action.move_to", "action.reveal_in_files",
                "action.export_note", "---", "action.move_to_trash"), labels(menu));
        item(menu, "action.reveal_in_files").fire();
        assertEquals(List.of("reveal a"), calls);
    }

    @Test
    void aDatabaseVaultHasNoRevealEntry() throws Exception {
        onFxThread(() -> aDatabaseVaultHasNoRevealEntryBody());
    }

    private void aDatabaseVaultHasNoRevealEntryBody() {
        ContextMenu menu = NoteContextMenuSupport.create(note("a"), actions(), false, key -> key, null);

        assertFalse(labels(menu).contains("action.reveal_in_files"));
        assertEquals("action.move_to_trash", labels(menu).get(labels(menu).size() - 1));
    }

    @Test
    void everyOtherEntryReachesItsAction() throws Exception {
        onFxThread(() -> everyOtherEntryReachesItsActionBody());
    }

    private void everyOtherEntryReachesItsActionBody() {
        ContextMenu menu = NoteContextMenuSupport.create(note("a"), actions(), false, key -> key, List.of());
        for (String label : List.of("action.pin_note", "action.add_favorite", "action.rename", "action.move_to",
                "action.move_to_trash")) {
            item(menu, label).fire();
        }
        assertEquals(List.of("pin a", "favorite a", "rename a", "move a", "trash a"), calls);
    }

    private static void onFxThread(Runnable work) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private NoteContextMenuSupport.Actions actions() {
        return new NoteContextMenuSupport.Actions(record("open"), record("newTab"), record("pin"),
                record("favorite"), record("privacy"), record("rename"), record("move"), record("reveal"),
                record("export"), record("trash"));
    }

    private Consumer<Note> record(String name) {
        return n -> calls.add(name + " " + n.getId());
    }

    private static List<String> labels(ContextMenu menu) {
        return menu.getItems().stream().map(i -> i instanceof SeparatorMenuItem ? "---" : i.getText()).toList();
    }

    private static MenuItem item(ContextMenu menu, String label) {
        return menu.getItems().stream().filter(i -> label.equals(i.getText())).findFirst().orElseThrow();
    }

    private static Note note(String id) {
        Note note = new Note(id, "content");
        note.setId(id);
        return note;
    }
}
