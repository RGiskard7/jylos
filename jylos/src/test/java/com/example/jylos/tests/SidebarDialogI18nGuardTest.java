package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SidebarDialogI18nGuardTest {

    private static final Path SIDEBAR_CONTROLLER = Path
            .of("src/main/java/com/example/jylos/ui/controller/SidebarController.java");

    @Test
    void sidebarShouldUseI18nDeleteDialogsAndNotMissingPermanentlyKey() throws IOException {
        String source = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);
        assertTrue(source.contains("dialog.delete_permanently.header"),
                "Sidebar should use dialog.delete_permanently.* keys for permanent delete.");
        assertTrue(source.contains("dialog.delete_folder.title"),
                "Sidebar should use dialog.delete_folder.* keys.");
        assertTrue(source.contains("dialog.delete_tag.title"),
                "Sidebar should use dialog.delete_tag.* keys.");
        assertFalse(source.contains("app.permanently"),
                "Sidebar should not reference non-existent app.permanently key.");
    }
}
