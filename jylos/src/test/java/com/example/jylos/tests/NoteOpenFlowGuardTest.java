package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class NoteOpenFlowGuardTest {

    private static final Path VIEW_TABS_SUPPORT = Path.of("src/main/java/com/example/jylos/ui/controller/TabsSupport.java");

    @Test
    void tabsSupportShouldDelegateNoteOpenDirectlyInsteadOfRepublishingEvents() throws IOException {
        String source = Files.readString(VIEW_TABS_SUPPORT, StandardCharsets.UTF_8);

        assertTrue(source.contains("host.openNote"));
        assertFalse(source.contains("NoteOpenRequestEvent"),
                "TabsSupport should delegate note-open requests directly to its owner.");
        assertFalse(source.contains("EventBus.getInstance()"),
                "TabsSupport should not use the global EventBus for one-to-one note-open delegation.");
    }
}
