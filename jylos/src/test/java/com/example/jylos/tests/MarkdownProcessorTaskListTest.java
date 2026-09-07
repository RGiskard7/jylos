package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.MarkdownProcessor;

/**
 * A GFM task list item ({@code - [ ] todo} / {@code - [x] done}) must render as a real,
 * disabled {@code <input type="checkbox">}, matching Obsidian/GitHub preview parity —
 * not as the literal three characters {@code [ ]}/{@code [x]}, which is what plain
 * CommonMark (no task-list extension loaded) renders it as.
 */
class MarkdownProcessorTaskListTest {

    @Test
    void unchekedTaskListItemRendersAsARealCheckbox() {
        String html = MarkdownProcessor.markdownToHtml("- [ ] Buy milk");

        assertTrue(html.contains("type=\"checkbox\""),
                "expected a real checkbox input, got: " + html);
        assertFalse(html.contains("[ ]"),
                "the literal bracket text must not remain in the rendered HTML: " + html);
    }

    @Test
    void checkedTaskListItemRendersAsACheckedCheckbox() {
        String html = MarkdownProcessor.markdownToHtml("- [x] Done already");

        assertTrue(html.contains("type=\"checkbox\""),
                "expected a real checkbox input, got: " + html);
        assertTrue(html.contains("checked"),
                "a completed task item must render its checkbox as checked: " + html);
        assertFalse(html.contains("[x]"),
                "the literal bracket text must not remain in the rendered HTML: " + html);
    }

    @Test
    void ordinaryBulletListItemsAreUnaffected() {
        String html = MarkdownProcessor.markdownToHtml("- Just a normal bullet");

        assertFalse(html.contains("type=\"checkbox\""),
                "a plain bullet with no [ ]/[x] marker must not become a checkbox: " + html);
        assertTrue(html.contains("<li>"), "expected a normal list item: " + html);
    }
}
