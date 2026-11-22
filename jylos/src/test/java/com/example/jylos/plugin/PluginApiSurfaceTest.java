package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * What a plugin may reach of the app besides its API packages: the headings of a note and a
 * line of Markdown as it shows, with the records and kinds those classes give back (nested
 * classes, named {@code Outer$Inner} when loaded); a nested class of an internal class is
 * still internal.
 */
class PluginApiSurfaceTest {

    @Test
    void theHeadingsAndTheInlineMarkdownAreThereWithTheirNestedClasses() {
        List<String> names = List.of(
                "com.example.jylos.util.NoteAnchors",
                "com.example.jylos.util.NoteAnchors$Heading",
                "com.example.jylos.util.InlineMarkdown",
                "com.example.jylos.util.InlineMarkdown$Run",
                "com.example.jylos.util.InlineMarkdown$Style",
                "com.example.jylos.ui.UiDialogs$Inner",
                "com.example.jylos.util.MarkdownPreview",
                "com.example.jylos.util.NoteAnchorsX");
        assertEquals(List.of(true, true, true, true, true, false, false, false),
                names.stream().map(PluginApiSurface::isAllowed).toList());
    }
}
