package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

/**
 * Saving a canvas tells the window it has no unsaved changes, as saving a note does, so the
 * status bar stops saying "Unsaved".
 */
class CanvasSaveStateTest {

    @Test
    void savingACanvasReportsItSaved(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Mapa.canvas"), "{\"nodes\":[],\"edges\":[]}", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));

        List<String> seen = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            new Scene(new Pane((javafx.scene.Parent) loader.load()), 900, 600);
            EditorController editor = loader.getController();
            List<Boolean> reported = new ArrayList<>();
            set(editor, "noteService", notes);
            set(editor, "noteModifiedAction", (Consumer<Note>) note -> reported.add(editor.isModified()));
            editor.loadNote(notes.getNoteById("Mapa.canvas").orElseThrow());
            set(editor, "isModified", true); // an edit made on the canvas
            reported.clear();

            editor.handleSave(false);
            return List.of("modified " + editor.isModified(), "reported " + reported);
        });

        // The window hears that the canvas is saved: what the status bar reads to stop
        // showing "Unsaved".
        assertEquals(List.of("modified false", "reported [false]"), seen);
    }

    /** A private canvas opens as its canvas while unlocked, and as the lock while locked. */
    @Test
    void aPrivateCanvasOpensAsACanvasWhileUnlocked(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Mapa.canvas"), "{\"nodes\":[],\"edges\":[]}", StandardCharsets.UTF_8);
        com.example.jylos.service.EncryptionService encryption = com.example.jylos.service.EncryptionService.getInstance();
        encryption.configure("clave-canvas-editor".toCharArray());
        try {
            NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                    new FolderDAOFileSystem(vault.toString()));
            Note canvas = notes.getNoteById("Mapa.canvas").orElseThrow();
            canvas.setPrivate(true);
            notes.updateNote(canvas);

            List<String> seen = onFx(() -> {
                List<String> out = new ArrayList<>();
                for (boolean locked : new boolean[] {false, true}) {
                    if (locked) {
                        encryption.lock();
                    }
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                            ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
                    new Scene(new Pane((javafx.scene.Parent) loader.load()), 900, 600);
                    EditorController editor = loader.getController();
                    set(editor, "noteService", notes);
                    editor.loadNote(notes.getNoteById("Mapa.canvas").orElseThrow());
                    Field view = EditorController.class.getDeclaredField("currentCanvasView");
                    view.setAccessible(true);
                    out.add((locked ? "locked" : "unlocked") + ": canvas " + (view.get(editor) != null));
                }
                return out;
            });

            assertEquals(List.of("unlocked: canvas true", "locked: canvas false"), seen);
        } finally {
            encryption.lock();
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = EditorController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
