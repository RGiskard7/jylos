package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class NoteOpenFlowGuardTest {

    private static final Path OVERLAY_SUPPORT = Path.of("src/main/java/com/example/jylos/ui/controller/OverlaySupport.java");

    @Test
    void overlaySupportShouldDelegateNoteOpenDirectlyInsteadOfRepublishingEvents() throws IOException {
        String source = Files.readString(OVERLAY_SUPPORT, StandardCharsets.UTF_8);

        assertTrue(source.contains("Consumer<Note> openNote"));
        assertFalse(source.contains("NoteOpenRequestEvent"),
                "OverlaySupport should delegate note-open requests directly to its owner.");
        assertFalse(source.contains("EventBus.getInstance()"),
                "OverlaySupport should not use the global EventBus for one-to-one note-open delegation.");
    }
}
