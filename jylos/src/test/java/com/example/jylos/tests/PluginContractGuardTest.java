package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class PluginContractGuardTest {

    private static final Path NOTE_EVENTS = Path.of("src/main/java/com/example/jylos/event/events/NoteEvents.java");
    private static final Path MAIN_CONTROLLER = Path.of("src/main/java/com/example/jylos/ui/controller/MainController.java");
    private static final Path PLUGINS_SOURCE_DIR = Path.of("..", "plugins-source");

    @Test
    void noteSelectionEventShouldRemainAvailableAsPluginExtensibilityContract() throws IOException {
        String noteEvents = Files.readString(NOTE_EVENTS, StandardCharsets.UTF_8);
        String mainController = Files.readString(MAIN_CONTROLLER, StandardCharsets.UTF_8);

        assertTrue(noteEvents.contains("class NoteSelectedEvent"),
                "NoteSelectedEvent should remain available for plugin fan-out/extensibility.");
        assertTrue(mainController.contains("publishNoteSelected(activeNote);"),
                "MainController should publish note selection fan-out for plugins after opening a note.");
    }

    @Test
    void pluginSourcesShouldNotDependOnRemovedInternalUiEvents() throws IOException {
        try (Stream<Path> files = Files.walk(PLUGINS_SOURCE_DIR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("NoteOpenRequestEvent"),
                        "Plugin sources should not depend on removed internal note-open request events: " + file);
                assertFalse(source.contains("NoteModifiedEvent"),
                        "Plugin sources should not depend on removed internal note-modified events: " + file);
                assertFalse(source.contains("UIEvents"),
                        "Plugin sources should not depend on removed UIEvents wrappers: " + file);
            }
        }
    }
}
