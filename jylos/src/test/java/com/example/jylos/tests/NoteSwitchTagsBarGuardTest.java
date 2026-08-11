package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Two real, reported per-note UI state bugs, guarded by source pattern rather than
 * runtime — both live inside methods that depend on a real JavaFX {@code Scene}/FXML
 * load ({@code EditorController.loadNote}, {@code MainController}'s save-event handler),
 * which this test suite does not stand up.
 */
class NoteSwitchTagsBarGuardTest {

    private static final Path EDITOR_CONTROLLER =
            Path.of("src/main/java/com/example/jylos/ui/controller/EditorController.java");
    private static final Path MAIN_CONTROLLER =
            Path.of("src/main/java/com/example/jylos/ui/controller/MainController.java");

    @Test
    void loadNoteResetsTheTagsBarOnEveryNoteTransition() throws IOException {
        String source = Files.readString(EDITOR_CONTROLLER, StandardCharsets.UTF_8);
        int loadNote = source.indexOf("public void loadNote(Note note) {");
        assertTrue(loadNote >= 0, "loadNote(Note) should exist");

        int nextMethod = source.indexOf("\n    public ", loadNote + 1);
        String body = source.substring(loadNote, nextMethod > 0 ? nextMethod : source.length());

        assertTrue(body.contains("initializeTagsBarCollapsed();"),
                "loadNote() must reset the tags bar toggle on every call — closing a note "
                        + "(setNoteOpen(false)) or opening an attachment (showAttachment()) each "
                        + "hide tagsContainer directly, without resetting toggleTagsBtn's own "
                        + "selected state, which otherwise stays visibly pressed with nothing to show.");
    }

    @Test
    void savingTheActiveNoteRefreshesItsVisibleTagChips() throws IOException {
        String source = Files.readString(MAIN_CONTROLLER, StandardCharsets.UTF_8);
        int handler = source.indexOf("eventBus.subscribe(NoteEvents.NoteSavedEvent.class");
        assertTrue(handler >= 0, "The NoteSavedEvent subscription should exist");

        int end = source.indexOf("}));", handler);
        String body = source.substring(handler, end > 0 ? end : source.length());

        assertTrue(body.contains("loadNoteTagsForNote"),
                "The active note's tags-bar chips are built once when it opens and never "
                        + "re-read the note's tag list on their own — a plain content save that "
                        + "picks up a newly typed inline #tag (see NoteDAOFileSystem's resync) "
                        + "left the visible chips stale until the note was closed and reopened.");
    }
}
