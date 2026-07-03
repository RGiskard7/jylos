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

    @Test
    void tagManagementShouldUseTagServiceInsteadOfNoteDao() throws IOException {
        String source = Files.readString(TAG_MANAGEMENT, StandardCharsets.UTF_8);

        assertFalse(source.contains("NoteDAO"),
                "TagManagement should not depend on NoteDAO when TagService already owns note-tag workflows.");
        assertFalse(source.contains("noteDAO."),
                "TagManagement should not call NoteDAO directly for note-tag workflows.");
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
    void editorControllerShouldLoadTagsThroughNoteService() throws IOException {
        String source = Files.readString(EDITOR_CONTROLLER, StandardCharsets.UTF_8);

        assertFalse(source.contains("noteDAO.fetchTags("),
                "EditorController should load note tags through NoteService, not NoteDAO.");
    }

    @Test
    void sidebarControllerShouldNotRefreshCachesAfterRestoreFlows() throws IOException {
        String source = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);

        assertFalse(source.contains("folderDAO.refreshCache()"),
                "SidebarController should not manually refresh FolderDAO caches after restore flows.");
        assertFalse(source.contains("noteDAO.refreshCache()"),
                "SidebarController should not manually refresh NoteDAO caches after restore flows.");
    }
}
