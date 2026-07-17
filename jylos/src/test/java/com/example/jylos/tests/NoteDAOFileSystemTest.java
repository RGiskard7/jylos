package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.exceptions.DataAccessException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class NoteDAOFileSystemTest {

    @TempDir
    Path tempDir;

    private NoteDAOFileSystem noteDAO;

    @BeforeEach
    public void setUp() {
        noteDAO = new NoteDAOFileSystem(tempDir.toString());
    }

    @Test
    public void testCreateNote() {
        Note note = new Note("Test Title", "Test Content");
        String id = noteDAO.createNote(note);

        assertNotNull(id);

        Note retrieved = noteDAO.getNoteById(id);
        assertNotNull(retrieved);
        assertEquals("Test Title", retrieved.getTitle());
        assertEquals("Test Content", retrieved.getContent());
    }

    @Test
    public void testUpdateNote() {
        Note note = new Note("Title", "Content");
        noteDAO.createNote(note);

        note.setTitle("New Title");
        note.setContent("New Content");
        noteDAO.updateNote(note);

        // ID might have changed due to rename, so rely on the note object's ID which
        // updateNote should have updated
        Note updated = noteDAO.getNoteById(note.getId());
        assertNotNull(updated, "Updated note should exist");
        assertEquals("New Title", updated.getTitle());
        assertEquals("New Content", updated.getContent());
    }

    @Test
    void updateFromLightweightMarkdownNotePreservesFullBody() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            body.append("Line ").append(i).append(" with enough content to exceed lightweight preview reads.\n");
        }
        Note note = new Note("Long Note", body.toString());
        String id = noteDAO.createNote(note);

        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note lightweight = reopened.fetchAllNotes().stream()
                .filter(n -> id.equals(n.getId()))
                .findFirst()
                .orElseThrow();

        lightweight.setTitle("Renamed Long Note");
        reopened.updateNote(lightweight);

        Note renamed = reopened.getNoteById(lightweight.getId());
        assertNotNull(renamed);
        assertEquals("Renamed Long Note", renamed.getTitle());
        assertEquals(body.toString(), renamed.getContent());
    }

    @Test
    void updateMissingMarkdownNoteRecreatesOriginalFile() throws Exception {
        Note note = new Note("Draft", "Initial");
        String id = noteDAO.createNote(note);
        Note opened = noteDAO.getNoteById(id);
        assertNotNull(opened);

        Files.delete(tempDir.resolve(id));
        opened.setContent("Unsaved edits after external delete");
        noteDAO.updateNote(opened);

        Note recreated = noteDAO.getNoteById(id);
        assertNotNull(recreated);
        assertEquals("Draft", recreated.getTitle());
        assertEquals("Unsaved edits after external delete", recreated.getContent());
    }

    @Test
    void updateMissingMarkdownNoteRecreatesDeletedParentFolder() throws Exception {
        Files.createDirectories(tempDir.resolve("Folder"));
        Note note = new Note("Draft", "Initial");
        note.setId("Folder/Draft.md");
        String id = noteDAO.createNote(note);
        Note opened = noteDAO.getNoteById(id);
        assertNotNull(opened);

        Files.delete(tempDir.resolve(id));
        Files.delete(tempDir.resolve("Folder"));

        opened.setContent("Recovered after parent folder delete");
        noteDAO.updateNote(opened);

        Note recreated = noteDAO.getNoteById(id);
        assertNotNull(recreated);
        assertEquals("Recovered after parent folder delete", recreated.getContent());
    }

    @Test
    void updateMissingCanvasNoteRecreatesOriginalFile() throws Exception {
        String canvasJson = """
                {
                  "nodes": [],
                  "edges": []
                }
                """;
        Note canvas = new Note("Board.canvas", canvasJson);
        String id = noteDAO.createNote(canvas);
        Note opened = noteDAO.getNoteById(id);
        assertNotNull(opened);

        Files.delete(tempDir.resolve(id));
        opened.setContent("""
                {
                  "nodes": [
                    { "id": "n1", "type": "text", "text": "kept" }
                  ],
                  "edges": []
                }
                """);
        noteDAO.updateNote(opened);

        Path recreatedPath = tempDir.resolve(id);
        assertTrue(Files.exists(recreatedPath));
        JsonObject root = JsonParser.parseString(Files.readString(recreatedPath)).getAsJsonObject();
        assertEquals("kept", root.getAsJsonArray("nodes").get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void updateMissingLightweightMarkdownNoteFailsWithoutRecreatingPartialFile() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            body.append("Line ").append(i).append(" of the real document.\n");
        }
        Note note = new Note("Long Draft", body.toString());
        String id = noteDAO.createNote(note);

        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note lightweight = reopened.fetchAllNotes().stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(!lightweight.isContentComplete(), "list notes must stay lightweight in this scenario");

        Files.delete(tempDir.resolve(id));
        lightweight.setTitle("Long Draft Renamed");

        assertThrows(DataAccessException.class, () -> reopened.updateNote(lightweight));
        assertTrue(!Files.exists(tempDir.resolve("Long Draft Renamed.md")),
                "A lightweight note must not recreate a missing Markdown file from partial state.");
    }

    @Test
    void updateMissingBinaryAttachmentDoesNotCreateEmptyFile() throws Exception {
        Path image = tempDir.resolve("image.png");
        Files.write(image, new byte[] { 1, 2, 3 });
        noteDAO.refreshCache();
        Note opened = noteDAO.getNoteById("image.png");
        assertNotNull(opened);

        Files.delete(image);
        opened.setTitle("renamed.png");

        assertThrows(DataAccessException.class, () -> noteDAO.updateNote(opened));
        assertTrue(!Files.exists(tempDir.resolve("renamed.png")),
                "Missing binary attachments must not be recreated as empty files.");
    }

    @Test
    void updateInvalidCanvasDoesNotOverwriteExistingFile() throws Exception {
        Note canvas = new Note("Board.canvas", "{\"nodes\":[],\"edges\":[]}");
        String id = noteDAO.createNote(canvas);
        Path path = tempDir.resolve(id);
        String original = Files.readString(path, StandardCharsets.UTF_8);

        Note opened = noteDAO.getNoteById(id);
        assertNotNull(opened);
        opened.setContent("{invalid json");

        assertThrows(DataAccessException.class, () -> noteDAO.updateNote(opened));
        assertEquals(original, Files.readString(path, StandardCharsets.UTF_8),
                "Invalid canvas JSON must fail without overwriting the existing file.");
    }

    @Test
    void fetchAllNotesToleratesInvalidFrontmatterWithoutThrowing() throws Exception {
        Path broken = tempDir.resolve("Broken.md");
        Files.writeString(broken, """
                ---
                bad:
                  - ok
                 broken
                ---

                body
                """);

        noteDAO.refreshCache();

        List<Note> notes = noteDAO.fetchAllNotes();
        Note listed = notes.stream()
                .filter(candidate -> "Broken.md".equals(candidate.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals("Broken", listed.getTitle());
        assertTrue(!listed.isContentComplete(), "Invalid frontmatter must degrade to a safe lightweight fallback.");
    }

    @Test
    public void testDeleteNote() {
        Note note = new Note("Title", "Content");
        String id = noteDAO.createNote(note);

        noteDAO.deleteNote(id);

        // Soft delete means it should exist in trash and marked as deleted
        List<Note> trash = noteDAO.fetchTrashNotes();
        assertEquals(1, trash.size());
        Note deleted = trash.get(0);
        assertTrue(deleted.isDeleted());

        noteDAO.permanentlyDeleteNote(deleted.getId());
        assertEquals(0, noteDAO.fetchTrashNotes().size());
    }

    @Test
    public void testFetchAllNotes() {
        noteDAO.createNote(new Note("N1", "C1"));
        noteDAO.createNote(new Note("N2", "C2"));

        List<Note> notes = noteDAO.fetchAllNotes();
        assertEquals(2, notes.size());
    }

    @Test
    public void testCreateNoteShouldPreserveUnicodeTitle() {
        Note note = new Note("Sin título", "Contenido");
        String id = noteDAO.createNote(note);

        assertNotNull(id);
        Note retrieved = noteDAO.getNoteById(id);
        assertNotNull(retrieved);
        assertEquals("Sin título", retrieved.getTitle());
    }

    @Test
    public void testGetNoteByIdIndexesOutgoingLinks() {
        // Outgoing [[wiki]] / [label](note) links must be pre-extracted on read so the
        // backlink index needs no second full-file read per note.
        Note note = new Note("Source", "See [[Alpha]] and [label](Beta).");
        String id = noteDAO.createNote(note);

        List<String> targets = noteDAO.getNoteById(id).getLinkTargets();
        assertNotNull(targets, "Full read must populate link targets");
        assertTrue(targets.contains("Alpha"), "Expected wiki-link target Alpha in " + targets);
        assertTrue(targets.contains("Beta"), "Expected markdown-link target Beta in " + targets);
    }

    @Test
    public void testLightweightNotesCarryLinkTargets() {
        noteDAO.createNote(new Note("Linker", "Points to [[Target]]."));

        // A fresh DAO over the same vault rebuilds its cache from disk (the lightweight
        // read path), which is what feeds the backlink index at startup.
        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note lightweight = reopened.fetchAllNotes().stream()
                .filter(n -> "Linker".equals(n.getTitle()))
                .findFirst().orElseThrow();
        assertNotNull(lightweight.getLinkTargets(), "List (lightweight) notes must carry link targets");
        assertTrue(lightweight.getLinkTargets().contains("Target"),
                "Expected Target in " + lightweight.getLinkTargets());
    }

    @Test
    public void testDeferredConstructorLoadsContentInBackground() throws Exception {
        Files.writeString(tempDir.resolve("Deferred.md"),
                "---\ntags: [x]\n---\nBody linking to [[Target]].", StandardCharsets.UTF_8);

        // Deferred mode: the cache is built metadata-only first, contents in background.
        NoteDAOFileSystem deferred = new NoteDAOFileSystem(tempDir.toString(), true);

        // The title is listable immediately from filename metadata.
        assertTrue(deferred.fetchAllNotes().stream().anyMatch(n -> "Deferred".equals(n.getTitle())),
                "metadata-only cache must list the note by title at once");

        // Once the background load finishes, content-derived data is populated.
        assertTrue(deferred.awaitContentLoaded(10_000), "background content load should complete");
        Note loaded = deferred.fetchAllNotes().stream()
                .filter(n -> "Deferred".equals(n.getTitle()))
                .findFirst().orElseThrow();
        assertNotNull(loaded.getLinkTargets(), "content load must populate link targets");
        assertTrue(loaded.getLinkTargets().contains("Target"),
                "Expected Target in " + loaded.getLinkTargets());
    }

    @Test
    public void testTags() {
        Note note = new Note("Tagged Note", "Content");
        note.addTag(new Tag("Work"));
        String id = noteDAO.createNote(note);

        Note retrieved = noteDAO.getNoteById(id);
        assertEquals(1, retrieved.getTags().size());
        assertEquals("Work", retrieved.getTags().get(0).getTitle());

        // Add another tag
        retrieved.addTag(new Tag("Personal"));
        noteDAO.updateNote(retrieved);

        Note updated = noteDAO.getNoteById(id);
        assertEquals(2, updated.getTags().size());
    }

    @Test
    void markdownFavoriteAndPinnedPersistAcrossRestart() {
        Note note = new Note("Flags", "Body");
        String id = noteDAO.createNote(note);

        Note loaded = noteDAO.getNoteById(id);
        loaded.setFavorite(true);
        loaded.setPinned(true);
        noteDAO.updateNote(loaded);

        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note persisted = reopened.getNoteById(loaded.getId());
        assertNotNull(persisted);
        assertTrue(persisted.isFavorite());
        assertTrue(persisted.isPinned());
    }

    @Test
    void canvasFavoriteAndPinnedPersistWithoutLosingContent() throws Exception {
        String canvasJson = """
                {
                  "nodes": [
                    { "id": "n1", "type": "text", "text": "hello" }
                  ],
                  "edges": []
                }
                """;
        Note canvas = new Note("Board.canvas", canvasJson);
        String id = noteDAO.createNote(canvas);

        Note loaded = noteDAO.getNoteById(id);
        assertNotNull(loaded);
        loaded.setFavorite(true);
        loaded.setPinned(true);
        noteDAO.updateNote(loaded);

        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note persisted = reopened.getNoteById(loaded.getId());
        assertNotNull(persisted);
        assertTrue(persisted.isFavorite());
        assertTrue(persisted.isPinned());

        JsonObject root = JsonParser.parseString(Files.readString(tempDir.resolve(loaded.getId()))).getAsJsonObject();
        assertTrue(root.has("nodes"));
        assertEquals(1, root.getAsJsonArray("nodes").size());
        assertEquals("hello", root.getAsJsonArray("nodes").get(0).getAsJsonObject().get("text").getAsString());
        assertTrue(!root.has("favorite"), "Canvas JSON must remain free of Jylos-only metadata keys.");
        assertTrue(!root.has("pinned"), "Canvas JSON must remain compatible with Obsidian.");
    }

    @Test
    void canvasListingUsesLightweightMetadataWithoutHydratingFullJson() {
        String canvasJson = """
                {
                  "nodes": [
                    { "id": "n1", "type": "text", "text": "hello" }
                  ],
                  "edges": []
                }
                """;
        Note canvas = new Note("Board.canvas", canvasJson);
        String id = noteDAO.createNote(canvas);

        Note loaded = noteDAO.getNoteById(id);
        loaded.setFavorite(true);
        noteDAO.updateNote(loaded);

        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note lightweight = reopened.fetchAllNotes().stream()
                .filter(note -> id.equals(note.getId()))
                .findFirst()
                .orElseThrow();

        assertTrue(lightweight.getContent() == null || lightweight.getContent().isEmpty(),
                "Canvas listings must not hydrate the full JSON body.");
        assertTrue(lightweight.isFavorite(), "Canvas metadata must come from the sidecar in lightweight listings.");
    }

    @Test
    void canvasLightweightMetadataUpdateDoesNotOverwriteCanvasJson() throws Exception {
        String canvasJson = """
                {
                  "nodes": [
                    { "id": "n1", "type": "text", "text": "hello" }
                  ],
                  "edges": []
                }
                """;
        Note canvas = new Note("Board.canvas", canvasJson);
        String id = noteDAO.createNote(canvas);

        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note lightweight = reopened.fetchAllNotes().stream()
                .filter(note -> id.equals(note.getId()))
                .findFirst()
                .orElseThrow();

        assertTrue(lightweight.getContent() == null || lightweight.getContent().isEmpty(),
                "The regression test must use a list-level canvas note without hydrated JSON.");

        lightweight.setFavorite(true);
        lightweight.setPinned(true);
        reopened.updateNote(lightweight);

        String persistedJson = Files.readString(tempDir.resolve(id));
        JsonObject root = JsonParser.parseString(persistedJson).getAsJsonObject();
        assertEquals("hello", root.getAsJsonArray("nodes").get(0).getAsJsonObject().get("text").getAsString());

        Note persisted = new NoteDAOFileSystem(tempDir.toString()).getNoteById(id);
        assertNotNull(persisted);
        assertTrue(persisted.isFavorite());
        assertTrue(persisted.isPinned());
    }

    @Test
    void binaryAttachmentFlagsPersistAcrossRestartAndRenameWithoutTouchingBytes() throws Exception {
        Path image = tempDir.resolve("diagram.png");
        byte[] originalBytes = new byte[] { 1, 2, 3, 4, 5 };
        Files.write(image, originalBytes);
        noteDAO.refreshCache();

        Note loaded = noteDAO.getNoteById("diagram.png");
        assertNotNull(loaded);
        loaded.setFavorite(true);
        loaded.setPinned(true);
        noteDAO.updateNote(loaded);

        loaded.setTitle("diagram-renamed.png");
        noteDAO.updateNote(loaded);

        NoteDAOFileSystem reopened = new NoteDAOFileSystem(tempDir.toString());
        Note persisted = reopened.getNoteById(loaded.getId());
        assertNotNull(persisted);
        assertTrue(persisted.isFavorite());
        assertTrue(persisted.isPinned());
        assertEquals("diagram-renamed.png", persisted.getTitle());
        assertTrue(Files.exists(tempDir.resolve("diagram-renamed.png")));
        assertTrue(java.util.Arrays.equals(originalBytes, Files.readAllBytes(tempDir.resolve("diagram-renamed.png"))));
    }

    @Test
    void binaryAttachmentMetadataSidecarIsRemovedOnPermanentDelete() throws Exception {
        Path pdf = tempDir.resolve("manual.pdf");
        Files.write(pdf, new byte[] { 9, 8, 7 });
        noteDAO.refreshCache();

        Note loaded = noteDAO.getNoteById("manual.pdf");
        assertNotNull(loaded);
        loaded.setFavorite(true);
        noteDAO.updateNote(loaded);

        noteDAO.deleteNote(loaded.getId());
        noteDAO.permanentlyDeleteNote(".trash/manual.pdf");

        Path sidecar = tempDir.resolve(".jylos").resolve("document-metadata.json");
        if (Files.exists(sidecar)) {
            String json = Files.readString(sidecar);
            assertTrue(!json.contains("manual.pdf"), "Deleted binary metadata must be removed from sidecar.");
        }
    }
}
