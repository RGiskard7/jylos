package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class EventBusUsageGuardTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java/com/example/jylos");
    private static final Path MAIN_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/MainController.java");
    private static final Path NOTES_LIST_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/NotesListController.java");
    private static final Path SIDEBAR_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/SidebarController.java");
    private static final Path TOOLBAR_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/ToolbarController.java");
    private static final Path EDITOR_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/EditorController.java");
    private static final Path PLUGIN_CONTEXT = Path.of("src/main/java/com/example/jylos/plugin/PluginContext.java");
    private static final Set<Path> GLOBAL_SINGLETON_ALLOWED = Set.of(
            Path.of("src/main/java/com/example/jylos/event/EventBus.java"),
            MAIN_CONTROLLER);

    @Test
    void eventBusSingletonShouldStayConfinedToBootstrapAndItsOwnImplementation() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("EventBus.getInstance(")) {
                    assertFalse(!GLOBAL_SINGLETON_ALLOWED.contains(file),
                            "Unexpected EventBus singleton usage outside the approved exceptions: " + file);
                }
            }
        }
    }

    @Test
    void singleOwnerUiRequestsShouldNotTravelThroughEventBus() throws IOException {
        String mainController = Files.readString(MAIN_CONTROLLER, StandardCharsets.UTF_8);
        String notesListController = Files.readString(NOTES_LIST_CONTROLLER, StandardCharsets.UTF_8);
        String sidebarController = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);
        String toolbarController = Files.readString(TOOLBAR_CONTROLLER, StandardCharsets.UTF_8);
        String editorController = Files.readString(EDITOR_CONTROLLER, StandardCharsets.UTF_8);
        String pluginContext = Files.readString(PLUGIN_CONTEXT, StandardCharsets.UTF_8);

        assertFalse(mainController.contains("NoteExportRequestEvent"),
                "MainController should handle note export requests via explicit wiring, not EventBus.");
        assertFalse(mainController.contains("PrivacyToggleRequestEvent"),
                "MainController should handle privacy-toggle requests via explicit wiring, not EventBus.");

        assertFalse(notesListController.contains("new NoteEvents.NoteSelectedEvent("),
                "NotesListController should notify note selection via wiring callback, not EventBus.");
        assertFalse(notesListController.contains("new NoteEvents.NoteExportRequestEvent("),
                "NotesListController should request note export via wiring callback, not EventBus.");
        assertFalse(notesListController.contains("new NoteEvents.PrivacyToggleRequestEvent("),
                "NotesListController should request privacy toggle via wiring callback, not EventBus.");
        assertFalse(notesListController.contains("new NoteEvents.NotesLoadedEvent("),
                "NotesListController should report notes-loaded updates via wiring callback, not EventBus.");
        assertTrue(notesListController.contains("Consumer<String> statusUpdateAction"),
                "NotesListController should expose an explicit status callback for one-to-one shell updates.");

        assertFalse(sidebarController.contains("new FolderEvents.FolderSelectedEvent("),
                "SidebarController should notify folder selection via wiring callback, not EventBus.");
        assertFalse(sidebarController.contains("new TagEvents.TagSelectedEvent("),
                "SidebarController should notify tag selection via wiring callback, not EventBus.");
        assertFalse(sidebarController.contains("new NoteEvents.TrashItemSelectedEvent("),
                "SidebarController should notify trash selection via wiring callback, not EventBus.");
        assertFalse(sidebarController.contains("NoteOpenRequestEvent"),
                "SidebarController should open recent/favorite notes via direct owner callback, not EventBus.");
        assertTrue(sidebarController.contains("Consumer<Note> openNoteAction"),
                "SidebarController should keep direct note-open callback wiring for recent/favorites.");
        assertTrue(sidebarController.contains("Consumer<String> statusUpdateAction"),
                "SidebarController should keep direct status callback wiring.");

        assertFalse(toolbarController.contains("UIEvents"),
                "ToolbarController should not depend on removed UIEvents wrappers.");
        assertTrue(toolbarController.contains("Runnable applyLightThemeAction"),
                "ToolbarController should receive theme changes via direct callbacks.");
        assertTrue(toolbarController.contains("Runnable applyDarkThemeAction"),
                "ToolbarController should receive theme changes via direct callbacks.");
        assertTrue(toolbarController.contains("Runnable applySystemThemeAction"),
                "ToolbarController should receive theme changes via direct callbacks.");

        assertFalse(editorController.contains("NoteModifiedEvent"),
                "EditorController should report note modifications via direct owner callback, not EventBus.");
        assertFalse(editorController.contains("NoteOpenRequestEvent"),
                "EditorController should open note links through its wired owner callback, not EventBus.");
        assertTrue(editorController.contains("Consumer<Note> noteModifiedAction"),
                "EditorController should expose a direct note-modified callback.");

        assertFalse(pluginContext.contains("NoteOpenRequestEvent"),
                "PluginContext.requestOpenNote should not publish note-open events anymore.");
        assertTrue(pluginContext.contains("noteOpenAction.accept(note)"),
                "PluginContext.requestOpenNote should delegate note opening through the owner callback.");
    }
}
