package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.models.Note;

/**
 * Inline {@code #tag} extraction must skip fenced code blocks — a shell comment
 * ({@code # note}), a CSS id selector, or a Dataview query written as {@code FROM #tag}
 * must not become a real tag on the note that merely contains the example.
 */
class FrontmatterInlineTagTest {

    private static List<String> tagTitles(Note note) {
        return note.getTags().stream().map(tag -> tag.getTitle()).collect(Collectors.toList());
    }

    @Test
    void extractsInlineTagsOutsideCodeFences() {
        Note note = FrontmatterHandler.parse("""
                # Title

                Real tag here: #project
                """);

        assertTrue(tagTitles(note).contains("project"));
    }

    @Test
    void ignoresHashInsideFencedCodeBlock() {
        Note note = FrontmatterHandler.parse("""
                # Title

                ```dataview
                TABLE rating
                FROM #project
                ```
                """);

        assertFalse(tagTitles(note).contains("project"),
                "A tag referenced only inside a fenced code block must not be attached to the note");
    }

    @Test
    void ignoresShellCommentsAndCssSelectorsInsideFences() {
        Note note = FrontmatterHandler.parse("""
                # Title

                ```bash
                # this is a comment, not a tag
                echo hello
                ```

                ```css
                #main-header { color: red; }
                ```
                """);

        assertFalse(tagTitles(note).contains("this"));
        assertFalse(tagTitles(note).contains("main-header"));
    }

    @Test
    void stillExtractsTagsAfterAFencedBlockCloses() {
        Note note = FrontmatterHandler.parse("""
                ```dataview
                FROM #ignored
                ```

                Real tag: #keep
                """);

        assertFalse(tagTitles(note).contains("ignored"));
        assertTrue(tagTitles(note).contains("keep"),
                "Fence-tracking must reset after the closing fence, not swallow the rest of the note");
    }

    @Test
    void tildeFencesAreAlsoRespected() {
        Note note = FrontmatterHandler.parse("""
                ~~~
                #notATag
                ~~~
                """);

        assertFalse(tagTitles(note).contains("notATag"));
    }
}
