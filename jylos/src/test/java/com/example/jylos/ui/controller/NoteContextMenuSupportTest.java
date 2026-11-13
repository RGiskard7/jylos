package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.runFx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

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
        runFx(() -> aFolderVaultOffersRevealInFilesAndItRevealsTheNoteBody());
    }

    private void aFolderVaultOffersRevealInFilesAndItRevealsTheNoteBody() {
        ContextMenu menu = NoteContextMenuSupport.create(note("a"), actions(), true, key -> key, List.of());

        assertEquals(List.of("action.open", "action.open_in_new_tab", "action.pin_note", "action.add_favorite",
                "action.make_private", "action.rename", "action.move_to", "action.duplicate", "action.copy_path",
                "action.open_default_app", "action.reveal_in_files", "action.export_note", "---",
                "action.move_to_trash"), labels(menu));
        for (String label : List.of("action.reveal_in_files", "action.duplicate", "action.copy_path",
                "action.open_default_app")) {
            item(menu, label).fire();
        }
        assertEquals(List.of("reveal a", "duplicate a", "copyPath a", "openDefault a"), calls);
    }

    @Test
    void aDatabaseVaultHasNoRevealEntry() throws Exception {
        runFx(() -> aDatabaseVaultHasNoRevealEntryBody());
    }

    private void aDatabaseVaultHasNoRevealEntryBody() {
        ContextMenu menu = NoteContextMenuSupport.create(note("a"), actions(), false, key -> key, null);

        assertFalse(labels(menu).contains("action.reveal_in_files"));
        assertFalse(labels(menu).contains("action.copy_path"), "no files to point at");
        assertFalse(labels(menu).contains("action.open_default_app"));
        assertTrue(labels(menu).contains("action.duplicate"));
        assertEquals("action.move_to_trash", labels(menu).get(labels(menu).size() - 1));
    }

    @Test
    void anImageIsNotDuplicatedAsANoteButACanvasIs() throws Exception {
        runFx(() -> {
            assertFalse(labels(NoteContextMenuSupport.create(note("foto.png"), actions(), true, key -> key, null))
                    .contains("action.duplicate"));
            assertTrue(labels(NoteContextMenuSupport.create(note("Mapa.canvas"), actions(), true, key -> key, null))
                    .contains("action.duplicate"));
        });
    }

    @Test
    void everyOtherEntryReachesItsAction() throws Exception {
        runFx(() -> everyOtherEntryReachesItsActionBody());
    }

    private void everyOtherEntryReachesItsActionBody() {
        ContextMenu menu = NoteContextMenuSupport.create(note("a"), actions(), false, key -> key, List.of());
        for (String label : List.of("action.pin_note", "action.add_favorite", "action.rename", "action.move_to",
                "action.move_to_trash")) {
            item(menu, label).fire();
        }
        assertEquals(List.of("pin a", "favorite a", "rename a", "move a", "trash a"), calls);
    }

    private NoteContextMenuSupport.Actions actions() {
        return new NoteContextMenuSupport.Actions(record("open"), record("newTab"), record("pin"),
                record("favorite"), record("privacy"), record("rename"), record("move"), record("reveal"),
                record("export"), record("trash"), record("duplicate"), record("copyPath"), record("openDefault"));
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
