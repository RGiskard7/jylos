package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class UiControllerCompositionGuardTest {

    private static final Path UI_CONTROLLER_DIR = Path.of("src/main/java/com/example/jylos/ui/controller");
    private static final Path MAIN_CONTROLLER = UI_CONTROLLER_DIR.resolve("MainController.java");
    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            "Controller.java", "Support.java", "Store.java", "Catalog.java", "Operations.java", "package-info.java");

    @Test
    void controllerFilesShouldFollowTheDeclaredRoleSuffixes() throws IOException {
        try (Stream<Path> files = Files.list(UI_CONTROLLER_DIR)) {
            List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                String name = file.getFileName().toString();
                assertTrue(ALLOWED_SUFFIXES.stream().anyMatch(name::endsWith),
                        "Unexpected ui/controller file role naming: " + name);
            }
        }
    }

    @Test
    void mainControllerShouldUseControllerWireEntryPointsForPrimaryComposition() throws IOException {
        String source = Files.readString(MAIN_CONTROLLER, StandardCharsets.UTF_8);

        assertTrue(source.contains("toolbarController.wire(eventBus);"));
        assertTrue(source.contains("sidebarController.wire("));
        assertTrue(source.contains("notesListController.wire("));
        assertTrue(source.contains("editorController.wire("));
        assertTrue(source.contains("graphViewController.wire("));

        assertFalse(source.contains("sidebarController.setEventBus("));
        assertFalse(source.contains("notesListController.setEventBus("));
        assertFalse(source.contains("editorController.setEventBus("));
        assertFalse(source.contains("graphViewController.setServices("));
    }
}
