package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards how the full-text search cache is invalidated.
 *
 * <p>{@code NotesListController} keeps two caches with different lifetimes: which notes
 * exist, and each note's lowercased body. Editing one note used to drop <em>every</em>
 * cached body, so the next search re-read the whole vault from disk — thousands of file
 * reads to reflect a single edit. The note-scoped events carry the note they are about,
 * so they now evict just that entry.</p>
 *
 * <p>A guard test rather than a behavioural one: the caches are private state inside an
 * FXML controller, the same reason {@code AllNotesContractGuardTest} checks this file by
 * source too. What it locks down is the wiring — which is exactly what a later edit would
 * get wrong, most easily by dropping the previous-id argument below.</p>
 */
class SearchCacheInvalidationGuardTest {

    private static final Path CONTROLLER =
            Path.of("src/main/java/com/example/jylos/ui/controller/NotesListController.java");

    @Test
    void noteScopedEventsEvictOnlyTheirOwnNote() throws IOException {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);

        for (String event : new String[] { "NoteCreatedEvent", "NoteSavedEvent", "NoteDeletedEvent",
                "TrashItemDeletedEvent" }) {
            int at = source.indexOf(event + ".class");
            assertTrue(at >= 0, event + " should still be subscribed to in NotesListController");
            String handler = source.substring(at, Math.min(source.length(), at + 400));
            assertTrue(handler.contains("markNotesListStale"),
                    event + " must evict only its own note's cached body, not the whole cache");
        }
    }

    @Test
    void theEvictionMethodActuallyEvicts() throws IOException {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);

        int at = source.indexOf("private void markNotesListStale(");
        assertTrue(at >= 0, "markNotesListStale should still exist");
        String body = source.substring(at, Math.min(source.length(), at + 400));
        // Checking the call sites alone is not enough: they would still name this method
        // if its body stopped evicting, and the wiring assertions above would stay green
        // while nothing was actually dropped from the cache.
        assertTrue(body.contains("fullContentCache.remove"),
                "markNotesListStale must drop the named notes from the content cache");
    }

    @Test
    void savingANoteAlsoForgetsTheBodyCachedUnderItsPreviousId() throws IOException {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);

        int at = source.indexOf("NoteSavedEvent.class");
        String handler = source.substring(at, Math.min(source.length(), at + 400));
        // A note's id is its path in the vault, so a save that renames or moves it changes
        // the id. Without the old one, its cached body would never be evicted at all.
        assertTrue(handler.contains("getPreviousNoteId"),
                "NoteSavedEvent must evict the previous id too, or a renamed note leaves a stale entry behind");
    }

    @Test
    void onlyAnUnscopedRefreshClearsEveryCachedBody() throws IOException {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);

        int at = source.indexOf("NotesRefreshRequestedEvent.class");
        assertTrue(at >= 0, "the refresh event should still be subscribed to");
        String handler = source.substring(at, Math.min(source.length(), at + 400));
        assertTrue(handler.contains("markAllNotesSearchCacheDirty"),
                "a refresh of unknown scope should still forget every cached body");

        // The whole point of the change: nothing note-scoped may reach the blanket clear.
        int refreshHandlerEnd = at + handler.indexOf("}));");
        String beforeRefresh = source.substring(0, at);
        assertFalse(beforeRefresh.contains("markAllNotesSearchCacheDirty()"),
                "no note-scoped subscription should call the blanket clear");
        assertTrue(refreshHandlerEnd > at, "the refresh handler should be a closed block");
    }
}
