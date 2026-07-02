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
    private static final Path GRAPH_CONTROLLER = UI_CONTROLLER_DIR.resolve("GraphController.java");
    private static final Path SIDEBAR_CONTROLLER = UI_CONTROLLER_DIR.resolve("SidebarController.java");
    private static final Path NOTES_LIST_CONTROLLER = UI_CONTROLLER_DIR.resolve("NotesListController.java");
    private static final Path EDITOR_CONTROLLER = UI_CONTROLLER_DIR.resolve("EditorController.java");
    private static final Path TOOLBAR_CONTROLLER = UI_CONTROLLER_DIR.resolve("ToolbarController.java");
    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            "Controller.java", "Support.java", "Operations.java", "package-info.java");

    @Test
    void controllerFilesShouldFollowTheDeclaredRoleSuffixes() throws IOException {
        try (Stream<Path> files = Files.walk(UI_CONTROLLER_DIR)) {
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

        assertTrue(source.contains("toolbarController.wire(eventBus,"));
        assertTrue(source.contains("sidebarController.wire("));
        assertTrue(source.contains("notesListController.wire("));
        assertTrue(source.contains("editorController.wire("));
        assertTrue(source.contains("graphViewController.wire(eventBus,"));

        assertFalse(source.contains("sidebarController.setEventBus("));
        assertFalse(source.contains("notesListController.setEventBus("));
        assertFalse(source.contains("editorController.setEventBus("));
        assertFalse(source.contains("graphViewController.setServices("));
    }

    @Test
    void mainControllerShouldDelegateSecondaryShellWorkflowsToSupports() throws IOException {
        String source = Files.readString(MAIN_CONTROLLER, StandardCharsets.UTF_8);

        assertTrue(source.contains("noteCreationSupport.wire("),
                "MainController should compose note creation workflows through NoteCreationSupport.");
        assertTrue(source.contains("documentWorkflowSupport.wire("),
                "MainController should compose local import/export workflows through DocumentWorkflowSupport.");

        assertFalse(source.contains("private java.util.List<Note> listTemplates("),
                "Template listing should stay out of MainController once extracted.");
        assertFalse(source.contains("private String templateContentByName("),
                "Template content resolution should stay out of MainController once extracted.");
        assertFalse(source.contains("private Note resolveFullNoteForExport("),
                "Export content resolution should stay out of MainController once extracted.");
    }

    @Test
    void primaryUiControllersShouldExposeWireAsTheirCompositionEntryPoint() throws IOException {
        assertControllerAvoidsPublicCompositionSetters(SIDEBAR_CONTROLLER,
                "public void setEventBus(",
                "public void setNoteService(",
                "public void setTagService(",
                "public void setFolderService(",
                "public void setFolderDAO(",
                "public void setNoteDAO(",
                "public void setBundle(");
        assertControllerAvoidsPublicCompositionSetters(NOTES_LIST_CONTROLLER,
                "public void setEventBus(",
                "public void setServices(",
                "public void setBundle(");
        assertControllerAvoidsPublicCompositionSetters(EDITOR_CONTROLLER,
                "public void setEventBus(",
                "public void setNoteDAO(",
                "public void setServices(",
                "public void setBundle(");
        assertControllerAvoidsPublicCompositionSetters(GRAPH_CONTROLLER,
                "public void setServices(",
                "public void setBundle(",
                "public void setOnOpenNote(",
                "public void setOnClose(",
                "public void setCurrentNoteIdSupplier(");
        assertControllerAvoidsPublicCompositionSetters(TOOLBAR_CONTROLLER,
                "public void setEventBus(");
    }

    @Test
    void graphControllerShouldNotReachForGlobalEventBus() throws IOException {
        String source = Files.readString(GRAPH_CONTROLLER, StandardCharsets.UTF_8);
        assertFalse(source.contains("EventBus.getInstance()"),
                "GraphController should receive EventBus via composition, not global lookup.");
    }

    private static void assertControllerAvoidsPublicCompositionSetters(Path file, String... forbiddenSetters)
            throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(source.contains("public void wire("),
                "Primary UI controller should expose wire(...) for composition: " + file.getFileName());
        for (String setter : forbiddenSetters) {
            assertFalse(source.contains(setter),
                    "Primary UI controller should not expose public composition setter " + setter + " in "
                            + file.getFileName());
        }
    }
}
