package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class NotesListControllerLoggingGuardTest {

    private static final Path NOTES_LIST_CONTROLLER = Path
            .of("src/main/java/com/example/jylos/ui/controller/NotesListController.java");

    @Test
    void notesListControllerShouldUseCentralizedLoggerConfig() throws IOException {
        String source = Files.readString(NOTES_LIST_CONTROLLER, StandardCharsets.UTF_8);
        assertTrue(source.contains("LoggerConfig.getLogger(NotesListController.class)"),
                "NotesListController should use LoggerConfig for consistent logging.");
        assertFalse(source.contains("Logger.getLogger(NotesListController.class.getName())"),
                "NotesListController should not use plain Logger.getLogger(className).");
    }
}
