package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.controller.EditorController;

import javafx.application.Platform;

/**
 * End to end through the editor's save path: a note file that changes on disk while it is
 * open (another app, a sync client, {@code git pull}) must not be overwritten by the
 * autosave, while a normal autosave keeps working.
 */
class EditorSaveConflictTest {

    @TempDir
    Path vault;

    @Test
    void autosaveDoesNotOverwriteAFileChangedOutsideJylos() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Path file = Files.writeString(vault.resolve("nota.md"), "original\n", StandardCharsets.UTF_8);
        List<String> statuses = new CopyOnWriteArrayList<>();

        boolean saved = onFxThread(() -> {
            EditorController editor = openInEditor(statuses);
            editInEditor(editor, "my version in the editor");
            Files.writeString(file, "edited in another app\n", StandardCharsets.UTF_8);
            return editor.handleSave(true);
        });

        assertFalse(saved, "autosave must report the note as still unsaved");
        assertTrue(Files.readString(file).contains("edited in another app"), "the external edit must survive");
        assertFalse(Files.readString(file).contains("my version in the editor"));
        assertTrue(statuses.contains("status.save_conflict_paused"), "the user must be told why: " + statuses);
    }

    @Test
    void autosaveStillSavesWhenNothingChangedOnDisk() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Path file = Files.writeString(vault.resolve("nota.md"), "original\n", StandardCharsets.UTF_8);
        List<String> statuses = new CopyOnWriteArrayList<>();

        boolean saved = onFxThread(() -> {
            EditorController editor = openInEditor(statuses);
            editInEditor(editor, "my version in the editor");
            return editor.handleSave(true);
        });

        assertTrue(saved);
        assertTrue(Files.readString(file).contains("my version in the editor"));
    }

    @Test
    void aSaveRightAfterAPreviousSaveIsNotMistakenForAConflict() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Path file = Files.writeString(vault.resolve("nota.md"), "original\n", StandardCharsets.UTF_8);
        List<String> statuses = new CopyOnWriteArrayList<>();

        boolean secondSaved = onFxThread(() -> {
            EditorController editor = openInEditor(statuses);
            editInEditor(editor, "first edit");
            assertTrue(editor.handleSave(true));
            editInEditor(editor, "second edit");
            return editor.handleSave(true);
        });

        assertTrue(secondSaved, "our own write must become the new baseline");
        assertTrue(Files.readString(file).contains("second edit"));
    }

    private EditorController openInEditor(List<String> statuses) throws Exception {
        NoteService noteService = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        EditorController editor = new EditorController();
        setField(editor, "noteService", noteService);
        setField(editor, "statusAction", (Consumer<String>) statuses::add);
        editor.loadNote(noteService.getNoteById("nota.md").orElseThrow());
        return editor;
    }

    private static void editInEditor(EditorController editor, String text) throws Exception {
        editor.getCurrentNote().setContent(text);
        setField(editor, "isModified", true);
    }

    private interface FxCall<T> {
        T call() throws Exception;
    }

    private static <T> T onFxThread(FxCall<T> call) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(call.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "FX call never completed");
        if (failure.get() != null) {
            throw new AssertionError("FX call failed", failure.get());
        }
        return result.get();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = EditorController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
