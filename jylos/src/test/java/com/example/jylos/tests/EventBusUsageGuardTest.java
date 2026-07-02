package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class EventBusUsageGuardTest {

    private static final Path MAIN_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/MainController.java");
    private static final Path UI_EVENT_SUPPORT = Path.of("src/main/java/com/example/jylos/ui/controller/UiEventSupport.java");
    private static final Path NOTES_LIST_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/NotesListController.java");
    private static final Path SIDEBAR_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/SidebarController.java");
    private static final Path TOOLBAR_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/ToolbarController.java");

    @Test
    void singleOwnerUiRequestsShouldNotTravelThroughEventBus() throws IOException {
        String mainController = Files.readString(MAIN_CONTROLLER, StandardCharsets.UTF_8);
        String uiEventSupport = Files.readString(UI_EVENT_SUPPORT, StandardCharsets.UTF_8);
        String notesListController = Files.readString(NOTES_LIST_CONTROLLER, StandardCharsets.UTF_8);
        String sidebarController = Files.readString(SIDEBAR_CONTROLLER, StandardCharsets.UTF_8);
        String toolbarController = Files.readString(TOOLBAR_CONTROLLER, StandardCharsets.UTF_8);

        assertFalse(mainController.contains("NoteExportRequestEvent"),
                "MainController should handle note export requests via explicit wiring, not EventBus.");
        assertFalse(mainController.contains("PrivacyToggleRequestEvent"),
                "MainController should handle privacy-toggle requests via explicit wiring, not EventBus.");

        assertFalse(uiEventSupport.contains("NoteSelectedEvent"),
                "UiEventSupport should not route note selection through EventBus when NotesListController has a single owner.");
        assertFalse(uiEventSupport.contains("NotesLoadedEvent"),
                "UiEventSupport should not route notes-loaded updates through EventBus when a direct callback is clearer.");
        assertFalse(uiEventSupport.contains("ShowCommandPaletteEvent"),
                "UiEventSupport should not route command palette requests through EventBus when ToolbarController has a single owner.");
        assertFalse(uiEventSupport.contains("ShowQuickSwitcherEvent"),
                "UiEventSupport should not route quick switcher requests through EventBus when ToolbarController has a single owner.");
        assertFalse(uiEventSupport.contains("ShowKeyboardShortcutsEvent"),
                "UiEventSupport should not route keyboard-shortcuts requests through EventBus when ToolbarController has a single owner.");
        assertFalse(uiEventSupport.contains("FolderSelectedEvent"),
                "UiEventSupport should not route folder selection through EventBus when SidebarController has a single owner.");
        assertFalse(uiEventSupport.contains("TagSelectedEvent"),
                "UiEventSupport should not route tag selection through EventBus when SidebarController has a single owner.");
        assertFalse(uiEventSupport.contains("TrashItemSelectedEvent"),
                "UiEventSupport should not route trash selection through EventBus when SidebarController has a single owner.");

        assertFalse(notesListController.contains("new NoteEvents.NoteSelectedEvent("),
                "NotesListController should notify note selection via wiring callback, not EventBus.");
        assertFalse(notesListController.contains("new NoteEvents.NoteExportRequestEvent("),
                "NotesListController should request note export via wiring callback, not EventBus.");
        assertFalse(notesListController.contains("new NoteEvents.PrivacyToggleRequestEvent("),
                "NotesListController should request privacy toggle via wiring callback, not EventBus.");
        assertFalse(notesListController.contains("new NoteEvents.NotesLoadedEvent("),
                "NotesListController should report notes-loaded updates via wiring callback, not EventBus.");

        assertFalse(sidebarController.contains("new FolderEvents.FolderSelectedEvent("),
                "SidebarController should notify folder selection via wiring callback, not EventBus.");
        assertFalse(sidebarController.contains("new TagEvents.TagSelectedEvent("),
                "SidebarController should notify tag selection via wiring callback, not EventBus.");
        assertFalse(sidebarController.contains("new NoteEvents.TrashItemSelectedEvent("),
                "SidebarController should notify trash selection via wiring callback, not EventBus.");

        assertFalse(toolbarController.contains("new UIEvents.ShowCommandPaletteEvent("),
                "ToolbarController should open the command palette via wiring callback, not EventBus.");
        assertFalse(toolbarController.contains("new UIEvents.ShowQuickSwitcherEvent("),
                "ToolbarController should open the quick switcher via wiring callback, not EventBus.");
        assertFalse(toolbarController.contains("new com.example.jylos.event.events.UIEvents.ShowKeyboardShortcutsEvent("),
                "ToolbarController should open keyboard shortcuts via wiring callback, not EventBus.");
    }
}
