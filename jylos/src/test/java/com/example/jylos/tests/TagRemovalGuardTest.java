package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * {@code TagManagement.removeTagFromNote} used to reject any tag with a {@code null} id
 * before its confirmation dialog even opened. A filesystem-vault tag typed inline
 * ({@code #project}) has no persistent id by design (see {@code Tag(String title)} /
 * {@code FrontmatterHandler.extractInlineTags}) — meaning the "x" on any inline tag chip
 * silently did nothing. Source-scanned rather than runtime-tested: the method under test
 * blocks on a real JavaFX confirmation {@link javafx.scene.control.Alert}.
 */
class TagRemovalGuardTest {

    private static final Path TAG_MANAGEMENT =
            Path.of("src/main/java/com/example/jylos/ui/controller/TagManagement.java");

    @Test
    void removeTagFromNoteMustNotRequireAnId() throws IOException {
        String source = Files.readString(TAG_MANAGEMENT, StandardCharsets.UTF_8);

        assertFalse(source.contains("tag == null || tag.getId() == null"),
                "This condition rejects every inline (id-less) tag before removal is even attempted.");
        assertTrue(source.contains("tag.getTitle() == null || tag.getTitle().isBlank()"),
                "The guard must fall back to the tag's title, matching the id-else-title "
                        + "pattern TagService.getNotesWithTag already uses for filesystem tags.");
    }
}
