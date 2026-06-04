package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SidebarDragDropContractGuardTest {

    private static final Path SIDEBAR_CONTROLLER = Path
            .of("src/main/java/com/example/jylos/ui/controller/SidebarController.java");

    @Test
    void dragAndDropShouldKeepPayloadContractAndSafetyRules() throws IOException {
        String source = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);

        assertTrue(source.contains("putString(\"folder:\" + folderId)"),
                "Folder drag source should publish folder payload contract.");
        assertTrue(source.contains("if (payload.startsWith(\"note:\"))"),
                "Drop handler should support note payload contract.");
        assertTrue(source.contains("if (payload.startsWith(\"folder:\"))"),
                "Drop handler should support folder payload contract.");
        assertTrue(source.contains("\"ALL_NOTES_VIRTUAL\""),
                "Drop should reject All Notes virtual destination.");
        assertTrue(source.contains("targetId.startsWith(sourceFolderId + \"/\")"),
                "Drop should reject dropping a folder inside one of its descendants.");
        assertTrue(source.contains("folderService.canMoveFolder(source.get(), targetFolder)"),
                "Folder drop should enforce anti-cycle validation.");
    }
}
