package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SidebarTrashRegressionGuardTest {

    private static final Path SIDEBAR_CONTROLLER = Path
            .of("src/main/java/com/example/jylos/ui/controller/SidebarController.java");

    @Test
    void setBundleShouldRefreshLocalizedMenus() throws IOException {
        String source = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);
        assertTrue(source.contains("refreshLocalizedMenus();"),
                "Sidebar setBundle should refresh localized menus to avoid showing i18n keys.");
    }

    @Test
    void trashTreeFolderIconsShouldShareExpandedCollapsedCssClasses() throws IOException {
        String source = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);
        assertTrue(source.contains("iconLabel.getStyleClass().add(isExp ? \"folder-expanded\" : \"folder-collapsed\");"),
                "Trash tree folder icons should use the same expanded/collapsed CSS classes as the main tree.");
    }

    @Test
    void deletingFolderFromSidebarShouldRefreshTrashTree() throws IOException {
        String source = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);
        assertTrue(source.contains("folderService.deleteFolder(f.getId());"),
                "Sidebar folder delete action should call folderService.deleteFolder.");
        assertTrue(source.contains("loadTrashTree();"),
                "Sidebar folder delete action should refresh both folder tree and trash tree.");
    }
}
