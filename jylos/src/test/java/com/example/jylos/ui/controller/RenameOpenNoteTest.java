package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.MarkdownEditorView;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

/**
 * Renaming the open note from the file explorer is Jylos's own write: the editor takes the
 * renamed file as what is on disk, so neither the rename nor the next save looks like a change
 * made outside Jylos.
 */
class RenameOpenNoteTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void renamingTheOpenNoteIsNotAnOutsideChange(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Nota.md"), "texto\n", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        List<String> status = new ArrayList<>();

        List<String> seen = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            new Scene(new Pane((Parent) loader.load()), 900, 600);
            EditorController editor = loader.getController();
            editor.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status::add,
                    List::of, false, enabled -> { }, null, null, null);
            editor.loadNote(notes.getNoteById("Nota.md").orElseThrow());

            // What the explorer's rename does, then what the window tells the open editor.
            Note listed = notes.getNoteById("Nota.md").orElseThrow();
            listed.setTitle("Renombrada");
            notes.updateNote(listed);
            editor.syncCurrentNoteIdentity(listed);

            List<String> out = new ArrayList<>();
            out.add("modified after the rename " + editor.isModified());
            // An edit typed in the editor after the rename.
            Field area = EditorController.class.getDeclaredField("noteContentArea");
            area.setAccessible(true);
            ((MarkdownEditorView) area.get(editor)).setText("texto editado\n");
            set(editor, "isModified", true);
            out.add("saved " + editor.handleSave(true));
            return out;
        });

        assertEquals(List.of("modified after the rename false", "saved true"), seen, "status: " + status);
        assertTrue(Files.readString(vault.resolve("Renombrada.md")).contains("texto editado"));
    }

    /** The same when the note had unsaved edits when it was renamed: they are saved, not refused. */
    @Test
    void unsavedEditsSurviveTheRename(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Nota.md"), "texto\n", StandardCharsets.UTF_8);
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        List<String> status = new ArrayList<>();

        boolean saved = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            new Scene(new Pane((Parent) loader.load()), 900, 600);
            EditorController editor = loader.getController();
            editor.wire(EventBus.getInstance(), notes, null, loader.getResources(), note -> { }, status::add,
                    List::of, false, enabled -> { }, null, null, null);
            editor.loadNote(notes.getNoteById("Nota.md").orElseThrow());
            Field area = EditorController.class.getDeclaredField("noteContentArea");
            area.setAccessible(true);
            ((MarkdownEditorView) area.get(editor)).setText("sin guardar\n");
            set(editor, "isModified", true);

            Note listed = notes.getNoteById("Nota.md").orElseThrow();
            listed.setTitle("Renombrada");
            notes.updateNote(listed);
            editor.syncCurrentNoteIdentity(listed);
            return editor.handleSave(true);
        });

        assertTrue(saved, "status: " + status);
        assertTrue(Files.readString(vault.resolve("Renombrada.md")).contains("sin guardar"));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = EditorController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
