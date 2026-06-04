package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SidebarFolderContextMenuGuardTest {

    private static final Path SIDEBAR_CONTROLLER = Path
            .of("src/main/java/com/example/jylos/ui/controller/SidebarController.java");

    @Test
    void folderContextMenuShouldExposeCreateNoteAndSubfolderActions() throws IOException {
        String source = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);

        assertTrue(source.contains("getString(\"action.new_note\")"),
                "Folder context menu should include 'new note'.");
        assertTrue(source.contains("getString(\"action.new_subfolder\")"),
                "Folder context menu should include 'new subfolder'.");
        assertTrue(source.contains("SystemActionEvent.ActionType.NEW_NOTE"),
                "Sidebar should trigger NEW_NOTE action from folder context menu.");
        assertTrue(source.contains("SystemActionEvent.ActionType.NEW_FOLDER"),
                "Sidebar should trigger NEW_FOLDER action from folder context menu.");
    }
}
