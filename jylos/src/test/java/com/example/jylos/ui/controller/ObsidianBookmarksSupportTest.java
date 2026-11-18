package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;

/**
 * Tools > Import Obsidian Bookmarks marks as favorites the notes bookmarked in Obsidian that
 * exist in the vault, leaves Obsidian's file as it was, and says how many it marked, how many
 * already were, how many it did not find and how many bookmarks are of other kinds, once the
 * views show the new favorites. It says when there are none or the file is not understood, and
 * does nothing outside a folder vault.
 */
class ObsidianBookmarksSupportTest {

    @TempDir
    Path vault;

    private final List<String> status = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    private NoteService notes() {
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    private void run(NoteService notes) {
        Map<ActionType, Runnable> handlers = new EnumMap<>(ActionType.class);
        ObsidianBookmarksSupport support = new ObsidianBookmarksSupport();
        support.register(handlers, () -> notes, key -> key, status::add, () -> events.add("refresh"), () -> null);
        support.reportWith(result -> events.add("report " + result));
        handlers.getOrDefault(ActionType.IMPORT_OBSIDIAN_BOOKMARKS, () -> status.add("no command")).run();
    }

    private void write(String path, String text) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    void theBookmarkedNotesBecomeFavoritesAndObsidiansFileStaysAsItWas() throws Exception {
        write("Plan.md", "plan\n");
        write("Libros/Ensayo.md", "ensayo\n");
        write("Ya.md", "ya\n");
        String json = "{\"items\":[{\"type\":\"file\",\"path\":\"Plan.md\"},{\"type\":\"group\",\"items\":["
                + "{\"type\":\"file\",\"path\":\"Libros/Ensayo.md\"},{\"type\":\"file\",\"path\":\"Ya.md\"}]},"
                + "{\"type\":\"file\",\"path\":\"Borrada.md\"},{\"type\":\"search\",\"query\":\"x\"}]}";
        write(".obsidian/bookmarks.json", json);
        NoteService before = notes();
        Note already = before.getNoteById("Ya.md").orElseThrow();
        before.toggleFavorite(already);

        run(notes());

        NoteService after = notes();
        assertEquals(List.of(true, true, true, false), List.of(
                after.getNoteById("Plan.md").orElseThrow().isFavorite(),
                after.getNoteById("Libros/Ensayo.md").orElseThrow().isFavorite(),
                after.getNoteById("Ya.md").orElseThrow().isFavorite(),
                after.getNoteById("Libros/Ensayo.md").orElseThrow().isPinned()));
        assertEquals(json, Files.readString(vault.resolve(".obsidian/bookmarks.json")));
        // The result is shown once the views are refreshed: a refresh of the notes list would
        // overwrite a message in the status bar.
        assertEquals(List.of("refresh", "report bookmarks.status.done"), events);
    }

    @Test
    void itSaysWhenThereAreNoneOrTheFileIsNotUnderstoodOrItIsNotAVault() throws Exception {
        run(notes());
        write(".obsidian/bookmarks.json", "{\"otra\": 1}");
        run(notes());
        run(null);

        assertEquals(List.of("bookmarks.status.none", "bookmarks.status.not_understood", "bookmarks.status.only_vault"),
                status);
        assertEquals(List.of(), events);
    }
}
