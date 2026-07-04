package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

class FileSystemDAOContractTest {

    @TempDir
    Path tempDir;

    private NoteDAOFileSystem noteDAO;
    private FolderDAOFileSystem folderDAO;
    private TagDAOFileSystem tagDAO;

    @BeforeEach
    void setUp() {
        String root = tempDir.toString();
        noteDAO = new NoteDAOFileSystem(root);
        folderDAO = new FolderDAOFileSystem(root);
        tagDAO = new TagDAOFileSystem(noteDAO);
    }

    @Test
    void moveNoteBetweenRootAndFolderKeepsConsistentIdsAndParent() {
        Folder folder = new Folder("Work");
        folderDAO.createFolder(folder);

        Note note = new Note("Task", "content");
        noteDAO.createNote(note);
        String rootId = note.getId();
        assertFalse(rootId.contains("/"));

        folderDAO.addNote(folder, note);
        assertTrue(note.getId().startsWith(folder.getId() + "/"));

        Folder detected = noteDAO.getFolderOfNote(note.getId());
        assertNotNull(detected);
        assertEquals(folder.getId(), detected.getId());

        folderDAO.removeNote(folder, note);
        assertFalse(note.getId().contains("/"));
        assertNotNull(note.getParent());
        assertEquals("ROOT", note.getParent().getId());
    }

    @Test
    void addAndRemoveTagsByIdAndRenameTagWorksInFilesystemMode() {
        Note note = new Note("Tagged", "content");
        noteDAO.createNote(note);

        noteDAO.addTag(note.getId(), "Work");
        List<Tag> tags = noteDAO.fetchTags(note.getId());
        assertEquals(1, tags.size());
        assertEquals("Work", tags.get(0).getTitle());

        Tag renameTag = new Tag("Work", "Office");
        tagDAO.updateTag(renameTag);

        Note reloaded = noteDAO.getNoteById(note.getId());
        assertNotNull(reloaded);
        assertTrue(reloaded.getTags().stream().anyMatch(t -> "Office".equals(t.getTitle())));

        noteDAO.removeTag(note.getId(), "Office");
        List<Tag> afterRemove = noteDAO.fetchTags(note.getId());
        assertEquals(0, afterRemove.size());
    }

    @Test
    void rootFolderContractIsConsistent() {
        Note note = new Note("Root note", "content");
        noteDAO.createNote(note);

        Folder rootById = folderDAO.getFolderById("ROOT");
        assertNotNull(rootById);
        assertEquals("ROOT", rootById.getId());

        Folder rootByNote = folderDAO.getFolderByNoteId(note.getId());
        assertNotNull(rootByNote);
        assertEquals("ROOT", rootByNote.getId());
    }

    @Test
    void restoreFolderWithNestedNotesShouldRecoverNotesInSubfolders() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder parent = folderService.createFolder("Project");
        Folder child = folderService.createSubfolder("Docs", parent);

        Note note = noteService.createNote("Plan", "content");
        folderService.addNoteToFolder(child, note);
        assertEquals(1, noteService.getNotesByFolder(child).size());

        folderService.deleteFolder(parent.getId());
        assertEquals(0, noteService.getAllNotes().size(),
                "After moving a folder tree to trash, active notes cache must be coherent.");

        folderService.restoreFolder(".trash/" + parent.getId());

        assertTrue(folderService.getFolderById(parent.getId()).isPresent());
        assertEquals(1, noteService.getNotesByFolder(child).size(),
                "Restoring folder tree must also restore notes visibility in nested subfolders.");
    }

    @Test
    void createNoteInEmptyFolderShouldBeVisibleImmediatelyForCountQueries() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder folder = folderService.createFolder("Inbox");
        assertEquals(0, noteService.getNotesByFolder(folder).size(),
                "A new folder must start with zero notes.");

        Note first = noteService.createNote("First", "content");
        folderService.addNoteToFolder(folder, first);
        assertEquals(1, noteService.getNotesByFolder(folder).size(),
                "After first create+assign, folder note count query must update immediately.");

        Note second = noteService.createNote("Second", "content");
        folderService.addNoteToFolder(folder, second);
        assertEquals(2, noteService.getNotesByFolder(folder).size(),
                "After second create+assign, folder note count query must update immediately.");
    }

    @Test
    void movingNoteIntoFolderMustNotPolluteFolderCacheWithNoteIds() {
        Folder project = new Folder("Project");
        folderDAO.createFolder(project);
        Folder docs = new Folder("Docs");
        folderDAO.createFolder(docs);

        Note note = new Note("Plan", "content");
        noteDAO.createNote(note);

        folderDAO.addNote(docs, note);

        List<Folder> folders = folderDAO.fetchAllFoldersAsList();
        assertEquals(2, folders.size(),
                "Folder cache must only contain folder paths, never note paths.");
        assertTrue(folders.stream().allMatch(f -> !f.getId().endsWith(".md")),
                "Folder IDs returned by FolderDAO must not include note file IDs.");
    }

    @Test
    void deleteAndRestoreFolderShouldSupportMixedPathSeparatorsInId() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);
        String docsId = docs.getId().replace("\\", "/");
        String docsIdWithBackslashes = docsId.replace("/", "\\");

        folderService.deleteFolder(docsIdWithBackslashes);
        assertTrue(folderService.getFolderById(docsId).isEmpty(),
                "Folder should be deleted even when ID separator differs from internal cache format.");

        folderService.restoreFolder(".trash\\" + docsIdWithBackslashes);
        assertTrue(folderService.getFolderById(docsId).isPresent(),
                "Folder should be restorable with mixed separator trash IDs.");
    }

    @Test
    void restoreNoteShouldSupportMixedPathSeparatorsInTrashId() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);

        Note note = noteService.createNote("Plan", "v1");
        folderService.addNoteToFolder(docs, note);
        String noteId = note.getId().replace("\\", "/");

        noteService.moveToTrash(note.getId());
        assertEquals(0, noteService.getNotesByFolder(docs).size());

        String trashIdWithBackslashes = (".trash/" + noteId).replace("/", "\\");
        noteService.restoreNote(trashIdWithBackslashes);

        assertEquals(1, noteService.getNotesByFolder(docs).size(),
                "Restoring note should work even if trash ID uses different separators.");
    }

    @Test
    void deleteNoteShouldSupportMixedPathSeparatorsInId() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);

        Note note = noteService.createNote("Plan", "v1");
        folderService.addNoteToFolder(docs, note);
        assertEquals(1, noteService.getNotesByFolder(docs).size());

        String noteIdWithBackslashes = note.getId().replace("/", "\\");
        noteDAO.deleteNote(noteIdWithBackslashes);

        assertEquals(0, noteService.getNotesByFolder(docs).size(),
                "Deleting note should work even if provided ID separators differ from internal format.");
        assertEquals(1, noteDAO.fetchTrashNotes().size(),
                "Deleted note must be visible in trash after mixed-separator delete.");
    }

    @Test
    void permanentlyDeleteNoteShouldSupportMixedPathSeparatorsInTrashId() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);

        Note note = noteService.createNote("Plan", "v1");
        folderService.addNoteToFolder(docs, note);
        noteService.moveToTrash(note.getId());
        assertEquals(1, noteDAO.fetchTrashNotes().size());

        String trashIdWithBackslashes = noteDAO.fetchTrashNotes().get(0).getId().replace("/", "\\");
        noteDAO.permanentlyDeleteNote(trashIdWithBackslashes);

        assertEquals(0, noteDAO.fetchTrashNotes().size(),
                "Permanent delete should work with mixed separator trash IDs.");
    }

    @Test
    void restoreNoteWithNameConflictShouldKeepBothNotes() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);

        Note original = noteService.createNote("Plan", "v1");
        folderService.addNoteToFolder(docs, original);

        noteService.moveToTrash(original.getId());
        assertEquals(0, noteService.getNotesByFolder(docs).size());

        Note replacement = noteService.createNote("Plan", "v2");
        folderService.addNoteToFolder(docs, replacement);
        assertEquals(1, noteService.getNotesByFolder(docs).size());

        String trashedId = noteService.getTrashNotes().get(0).getId();
        noteService.restoreNote(trashedId);

        List<Note> notes = noteService.getNotesByFolder(docs);
        assertEquals(2, notes.size(),
                "Restoring a trashed note with name conflict must preserve existing note and restore as renamed copy.");
    }

    @Test
    void createNoteShouldResolveSuggestedFolderIdWithBackslashes() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);

        Note note = new Note("Plan", "v1");
        note.setId((docs.getId() + "/Plan.md").replace("/", "\\"));
        noteService.createNote(note);

        assertTrue(note.getId().replace("\\", "/").startsWith(docs.getId().replace("\\", "/") + "/"),
                "Suggested note ID with backslashes must still resolve to the expected folder.");
        assertEquals(1, noteService.getNotesByFolder(docs).size(),
                "Created note must be immediately visible in target folder after mixed-separator suggested ID.");
    }

    @Test
    void updateAndGetNoteByIdShouldSupportMixedSeparators() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);

        Note note = noteService.createNote("Plan", "v1");
        folderService.addNoteToFolder(docs, note);

        String canonicalId = note.getId().replace("\\", "/");
        String mixedId = canonicalId.replace("/", "\\");

        Note loadedByMixed = noteDAO.getNoteById(mixedId);
        assertNotNull(loadedByMixed, "getNoteById must resolve mixed separators.");
        assertEquals("Plan", loadedByMixed.getTitle());

        loadedByMixed.setContent("v2");
        loadedByMixed.setId(mixedId);
        noteDAO.updateNote(loadedByMixed);

        Note reloaded = noteDAO.getNoteById(canonicalId);
        assertNotNull(reloaded);
        assertTrue(reloaded.getContent().contains("v2"),
                "updateNote must persist changes when note ID uses mixed separators.");
    }

    @Test
    void createFolderShouldResolveParentIdWithBackslashes() {
        Folder project = new Folder("Project");
        folderDAO.createFolder(project);

        Folder child = new Folder("Docs");
        child.setParent(new Folder(project.getId().replace("/", "\\"), "Project"));
        folderDAO.createFolder(child);

        assertTrue(child.getId().replace("\\", "/").startsWith(project.getId().replace("\\", "/") + "/"),
                "Creating folder with parent ID in backslash format must preserve hierarchy.");
    }

    @Test
    void getFolderByNoteIdShouldSupportMixedSeparators() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);
        Note note = noteService.createNote("Plan", "v1");
        folderService.addNoteToFolder(docs, note);

        String noteIdWithBackslashes = note.getId().replace("/", "\\");
        Folder detected = folderDAO.getFolderByNoteId(noteIdWithBackslashes);
        assertNotNull(detected);
        assertEquals(docs.getId().replace("\\", "/"), detected.getId().replace("\\", "/"),
                "Folder detection by note ID must work with mixed separators.");
    }

    @Test
    void renameNoteShouldNotLeaveStaleCacheEntries() {
        FolderService folderService = new FolderService(folderDAO, noteDAO);
        NoteService noteService = new NoteService(noteDAO, folderDAO);

        Folder project = folderService.createFolder("Project");
        Folder docs = folderService.createSubfolder("Docs", project);
        Note note = noteService.createNote("Plan", "v1");
        folderService.addNoteToFolder(docs, note);

        Note loaded = noteDAO.getNoteById(note.getId());
        assertNotNull(loaded);
        loaded.setTitle("Plan Renamed");
        loaded.setContent("v2");
        noteDAO.updateNote(loaded);

        List<Note> all = noteDAO.fetchAllNotes();
        assertEquals(1, all.size(),
                "Renaming a note must not leave stale cache entries with old IDs.");
        assertTrue(all.get(0).getId().replace("\\", "/").contains("Plan Renamed"),
                "After rename, only the new normalized note ID should remain in cache.");
        assertEquals(1, noteService.getNotesByFolder(docs).size(),
                "Folder queries must remain coherent after rename operations.");
    }

    @Test
    void createNoteWritesAttachmentRawWithoutFrontmatterOrMdSuffix() throws Exception {
        String json = "{\n\t\"nodes\":[],\n\t\"edges\":[]\n}";
        Note canvas = new Note("Board.canvas", json);
        String id = noteDAO.createNote(canvas);

        // The id keeps the .canvas extension (no ".md" appended).
        assertTrue(id.endsWith(".canvas"), "expected a .canvas id, got: " + id);

        Path file = tempDir.resolve(id.replace("/", java.io.File.separator));
        assertTrue(java.nio.file.Files.exists(file), "canvas file should exist on disk");

        // Written verbatim: raw JSON, no YAML frontmatter wrapper.
        String onDisk = java.nio.file.Files.readString(file);
        assertEquals(json, onDisk);
        assertFalse(onDisk.startsWith("---"), "attachment must not be wrapped in frontmatter");
    }

    @Test
    void updateAttachmentKeepsCanvasExtensionAndWritesRawJson() throws Exception {
        String initialJson = "{\"nodes\":[],\"edges\":[]}";
        Note canvas = new Note("Board.canvas", initialJson);
        String id = noteDAO.createNote(canvas);

        Note loaded = noteDAO.getNoteById(id);
        assertNotNull(loaded);

        String updatedJson = "{\"nodes\":[{\"id\":\"1\"}],\"edges\":[]}";
        loaded.setTitle("Roadmap");
        loaded.setContent(updatedJson);
        noteDAO.updateNote(loaded);

        assertTrue(loaded.getId().endsWith("Roadmap.canvas"),
                "Canvas rename must preserve the .canvas extension.");

        Path file = tempDir.resolve(loaded.getId().replace("/", java.io.File.separator));
        assertTrue(java.nio.file.Files.exists(file), "renamed canvas file should exist on disk");
        assertEquals(updatedJson, java.nio.file.Files.readString(file),
                "Canvas updates must be written verbatim, without Markdown frontmatter.");
    }

    @Test
    void updateAttachmentMustNotAllowChangingCanvasExtension() throws Exception {
        Note canvas = new Note("Board.canvas", "{\"nodes\":[],\"edges\":[]}");
        String id = noteDAO.createNote(canvas);

        Note loaded = noteDAO.getNoteById(id);
        assertNotNull(loaded);

        loaded.setTitle("Roadmap.md");
        noteDAO.updateNote(loaded);

        assertTrue(loaded.getId().endsWith("Roadmap.canvas"),
                "Renaming a canvas must preserve the .canvas file type even if the typed title includes another extension.");
    }

    @Test
    void folderAndTagListingsNeverExposeEncryptedBodies() {
        NoteService noteService = new NoteService(noteDAO, folderDAO);
        Folder folder = new Folder("Private");
        folderDAO.createFolder(folder);

        // A note whose stored body is an encrypted payload (JENC1: prefix). The listing
        // paths must replace it with the lock placeholder, never surface the ciphertext.
        Note secret = new Note("Secret", com.example.jylos.service.EncryptionService.PREFIX + "Zm9vYmFy");
        noteService.createNote(secret);
        folderDAO.addNote(folder, secret);

        List<Note> inFolder = noteService.getNotesByFolder(folder);
        assertEquals(1, inFolder.size());
        assertEquals(NoteService.LOCKED_PLACEHOLDER, inFolder.get(0).getContent(),
                "folder listing must scrub encrypted bodies to the lock placeholder");

        // getAllNotes must scrub too (regression guard for the shared helper).
        assertTrue(noteService.getAllNotes().stream()
                .filter(n -> "Secret".equals(n.getTitle()))
                .allMatch(n -> NoteService.LOCKED_PLACEHOLDER.equals(n.getContent())));
    }
}
