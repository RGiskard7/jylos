package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UiServiceBoundaryGuardTest {

    private static final Path TAG_MANAGEMENT = Path.of("src/main/java/com/example/jylos/ui/controller/TagManagement.java");
    private static final Path FOLDER_OPERATIONS = Path.of("src/main/java/com/example/jylos/ui/controller/FolderOperations.java");
    private static final Path EDITOR_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/EditorController.java");
    private static final Path SIDEBAR_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/SidebarController.java");
    private static final Path NOTE_DAO_FILESYSTEM = Path.of("src/main/java/com/example/jylos/data/dao/filesystem/NoteDAOFileSystem.java");

    @Test
    void tagManagementShouldUseTagServiceInsteadOfNoteDao() throws IOException {
        String source = Files.readString(TAG_MANAGEMENT, StandardCharsets.UTF_8);

        assertFalse(source.contains("NoteDAO"),
                "TagManagement should not depend on NoteDAO when TagService already owns note-tag workflows.");
        assertFalse(source.contains("noteDAO."),
                "TagManagement should not call NoteDAO directly for note-tag workflows.");
    }

    @Test
    void noteServiceShouldNotOwnNoteTagRelationshipWorkflows() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/example/jylos/service/NoteService.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("public List<Tag> getNoteTags("),
                "NoteService should not own note-tag lookup once TagService is canonical.");
        assertFalse(source.contains("public List<Tag> getTagsForNote("),
                "NoteService should not expose note-tag lookup once TagService is canonical.");
        assertFalse(source.contains("public void addTagToNote("),
                "NoteService should not mutate note-tag relationships once TagService is canonical.");
        assertFalse(source.contains("public void removeTagFromNote("),
                "NoteService should not mutate note-tag relationships once TagService is canonical.");
    }

    @Test
    void folderOperationsShouldUseFolderServiceInsteadOfFolderDao() throws IOException {
        String source = Files.readString(FOLDER_OPERATIONS, StandardCharsets.UTF_8);

        assertFalse(source.contains("FolderDAO"),
                "FolderOperations should not depend on FolderDAO when FolderService already owns folder creation.");
        assertFalse(source.contains("folderDAO."),
                "FolderOperations should not call FolderDAO directly for folder creation.");
    }

    @Test
    void editorControllerShouldLoadTagsThroughTagServiceWithoutDaoResidue() throws IOException {
        String source = Files.readString(EDITOR_CONTROLLER, StandardCharsets.UTF_8);

        assertFalse(source.contains("noteDAO.fetchTags("),
                "EditorController should load note tags through TagService, not NoteDAO.");
        assertFalse(source.contains("NoteDAO"),
                "EditorController should not retain NoteDAO once tag loading is owned by TagService.");
        assertFalse(source.contains("noteService.getTagsForNote("),
                "EditorController should not load note tags through NoteService once TagService is canonical.");
        assertFalse(source.contains("noteDAO."),
                "EditorController should not keep direct NoteDAO usage.");
        assertFalse(source.contains("noteService.getAllNotes()"),
                "EditorController should not rebuild global note-title caches from NoteService on note open.");
        assertFalse(source.contains("Files.writeString("),
                "EditorController must persist vault documents through NoteService/DAO, not direct file writes.");
    }

    @Test
    void sidebarControllerShouldNotRefreshCachesOrRetainDaoWiring() throws IOException {
        String source = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);

        assertFalse(source.contains("folderDAO.refreshCache()"),
                "SidebarController should not manually refresh FolderDAO caches after restore flows.");
        assertFalse(source.contains("noteDAO.refreshCache()"),
                "SidebarController should not manually refresh NoteDAO caches after restore flows.");
        assertFalse(source.contains("FolderDAO"),
                "SidebarController should not retain FolderDAO wiring once services own the workflow.");
        assertFalse(source.contains("NoteDAO"),
                "SidebarController should not retain NoteDAO wiring once services own the workflow.");
    }

    @Test
    void filesystemDaoHotReadsShouldNotRunGlobalCachePruning() throws IOException {
        String source = Files.readString(NOTE_DAO_FILESYSTEM, StandardCharsets.UTF_8);

        assertFalse(source.contains("public List<Note> fetchNotesByFolderId(String folderId) {\n        pruneStaleCacheEntriesIfNeeded();"),
                "Folder note listing should not trigger a global filesystem cache prune on the hot path.");
        assertFalse(source.contains("public List<Note> fetchAllNotes() {\n        pruneStaleCacheEntriesIfNeeded();"),
                "Global note listing should not trigger a global filesystem cache prune on the hot path.");
    }
}
