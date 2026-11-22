package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.InlineMarkdown.Run;
import com.example.jylos.util.InlineMarkdown.Style;

/**
 * A line of Markdown (a heading's title) as the text it shows and how each piece looks: the
 * marks ({@code **}, {@code `}, {@code ==}, {@code [[ ]]}…) are gone, a link shows what the
 * reading view shows for it.
 */
class InlineMarkdownTest {

    /** Each run as "text" or "text:STYLE+STYLE", in order, for a readable comparison. */
    private static List<String> shown(String markdown) {
        return InlineMarkdown.runs(markdown).stream()
                .map(run -> run.styles().isEmpty() ? run.text()
                        : run.text() + ":" + run.styles().stream().map(Style::name).sorted()
                                .collect(Collectors.joining("+")))
                .toList();
    }

    @Test
    void theMarksAreGoneAndEachPieceKeepsItsLook() {
        assertEquals(List.of("algo:BOLD", " y ", "más:ITALIC", " ", "x:CODE", " ", "no:STRIKETHROUGH", " ",
                "sí:HIGHLIGHT"), shown("**algo** y *más* `x` ~~no~~ ==sí=="));
        assertEquals(List.of("a :BOLD", "b:BOLD+ITALIC"), shown("**a *b***"));
    }

    @Test
    void aLinkShowsWhatTheReadingViewShows() {
        assertEquals(List.of("Ver ", "esto:LINK", ", ", "Plan:LINK", ", ", "Plan › Fase:LINK", " y ", "web:LINK"),
                shown("Ver [[Nota|esto]], [[A/Plan]], [[Plan#Fase]] y [web](https://example.com)"));
        assertEquals(List.of("Idea:BOLD+LINK"), shown("**[[Idea]]**"));
    }

    @Test
    void whatIsNotAMarkStaysAsItIs() {
        assertEquals(List.of("Sin formato #etiqueta"), shown("Sin formato #etiqueta"));
        assertEquals(List.of("1. Intro"), shown("1. Intro"), "a title is not a list");
        assertEquals(List.of("- x ", "y:BOLD"), shown("- x **y**"));
        assertEquals(List.of("*no* x"), shown("\\*no\\* <b>x</b>"));
        assertEquals(List.of("[[A]]:CODE"), shown("`[[A]]`"), "a link inside code is code");
        assertEquals(List.of(), shown(""));
        assertEquals(List.of(), shown(null));
    }

    /** The plain text is what the reading view takes for the heading's anchor. */
    @Test
    void thePlainTextIsTheRunsTogether() {
        assertEquals("Ver esto y x", InlineMarkdown.plainText("Ver **[[Nota|esto]]** y `x`"));
        assertEquals("", InlineMarkdown.plainText(null));
        assertEquals(Set.of(), new Run("a", Set.of()).styles());
    }
}
